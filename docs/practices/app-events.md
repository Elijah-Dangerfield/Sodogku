# Client app events

The registry of structured events the client emits for product analytics. One event = one
`logEvent(name, attrs)` call (the extension in `:libraries:core` `logging/AppEvents.kt`) riding
the normal KLog tree system: it lands in logcat/os_log, as a Sentry breadcrumb, and — via
`GrafanaLogTree` in `:libraries:telemetry:impl` — as an OTLP log record in Grafana Cloud Loki.
Query conventions are in [`observability.md`](observability.md).

Dashboard queries treat this page as the source of truth for names and attributes. Names are
dot-namespaced snake_case; every record automatically carries `session_id` + `install_id` +
`is_offline` (per-record) plus resource attributes (`service.name="sodogku-client"`,
deployment environment, version, platform). `is_offline` is `AppState.isOffline` captured **at
emit time** — records that ship later from the disk buffer still say what connectivity looked
like when the event happened, so reliability funnels can segment "emitted offline" without span
archaeology.

**Delivery is durable, effectively at-least-once.** The export chain is batch → disk buffer →
OTLP: every batch is written to a file-backed buffer (`<files>/telemetry/…`, via
`durableLogRecordProcessor`) before export and deleted only after the gateway acknowledges it, so
events emitted offline survive process death and ship on a later launch or flush tick. Retention
is the library's defaults — 100 buffered batches, 30-day max age — after which oldest batches are
dropped. A record can rarely ship twice (export acknowledged but the process dies before the
buffer delete), so dashboards counting events should tolerate the odd duplicate rather than
assume exactly-once. Two edges remain lossy by design: records ride in RAM for up to one flush
tick (5s) before reaching disk, and `TelemetryBackgroundFlusher` closes most of that window by
force-flushing the pipe (RAM → disk → export attempt) on every app background — the last reliable
moment before the OS suspends or kills the process.

**Deliberate pipeline calls** (so nobody re-litigates them blind):

- **Batch tuning stays at library defaults** (2048-record queue, 5s flush, 512-record export
  batches, 30s export timeout). Typical volume is a handful of events per user-minute; the
  defaults are sized far above it and the 5s RAM window is bounded by the background flush.
- **Exports are NOT gated on `AppState.isOffline`.** Tempting (skip doomed POSTs while offline),
  but `isOffline` also trips on *backend* unreachability — and surviving backend outages is the
  whole reason this pipe goes direct to Grafana rather than through our server. A failed export
  while offline just stays in the buffer; the DNS failure is contained by
  `FailSafeLogRecordExporter`.
- **`telemetry.appEventsEnabled` + `appEventsSampleRate` stay separate.** The flag is an instant
  kill switch for library bugs / ingest incidents and reads as one in the QA menu; the rate is a
  gradual volume dial. Collapsing them makes the emergency lever a magic number.
- **iOS `previous_exit` is a day-granular MetricKit sample, not per-run truth.** iOS has no
  per-launch exit API, so `IosPreviousExitProvider` subscribes to `MXAppExitMetric`, classifies
  each day-window's foreground exits to the most severe (crash > anr > oom > clean), persists the
  result, and the next launch reports it exactly once (re-reporting every launch would multiply
  one crash by launch frequency). Background jetsam kills are deliberately excluded — routine on
  iOS, they'd read as fake OOMs next to Android's user-perceived `REASON_LOW_MEMORY`. MetricKit
  never delivers on the simulator; only real devices produce non-unknown values.

**Rules for adding events:** emit through the `logEvent` extension only (never a raw
`EXTRA_APP_EVENT` extra), fire on user actions / state transitions — never per-frame, per-poll,
or per-flow-emission — and add the event here in the same change. Client events answer
intent/funnel/abandonment questions; the backend DB stays source-of-truth for anything already in
a ledger.

## Engagement & session shape

| Event | Attributes | Fires |
|---|---|---|
| `app.launched` | `cold_start` (always true), `previous_exit` (clean/crash/anr/oom/unknown) | Once per cold start, on the boot foreground (`AppLaunchedEmitter`) — after the session tracker rolls session #1, so it shares the boot's `session_id` with every other event (it used to fire at DI init and land orphaned on a pre-rollover id). Doubles as the pipeline smoke test. `previous_exit` comes from Android's historical exit reasons (API 30+; older devices report `unknown`); **iOS derives it from MetricKit**, day-granular and up to 24h late — most iOS launches say `unknown`. Always segment by platform before reading exit rates |
| `app.foregrounded` | `cold_start` | Every foreground (`LifecycleAppEventLogger`); `cold_start=true` on the boot foreground. Count users/sessions from this event, not `app.launched` |
| `app.backgrounded` | `session_duration_sec` | Every background; whole seconds since the matching foreground (monotonic clock), so session length is a direct query — no span join. Omitted in the (shouldn't-happen) case of a background with no prior foreground |

## Reliability from the client's chair

The events that motivated shipping direct-to-Grafana: what never reaches the backend.

| Event | Attributes | Fires |
|---|---|---|
| `net.backend_unreachable` | `operation`, `error_kind` (timeout / exception class) | Shared `NetworkCall` failure path, non-HTTP failures only — an HTTP status IS reachability |
| `net.offline_banner` | `visible`, `os_online`, `backend_reachable` | Each edge of the app-wide offline banner (`AppStateImpl`), carrying which signal drove it |
| `conn.regained` | — | Reserved for offline→online recovery signals (`ConnectivityEdgeDispatcher` drives the app event; emit here if you need it in Loki). Apps with a long-lived socket should extend the `conn.*` namespace: `conn.reconnecting` (`attempt`), `conn.recovered` (`attempts`, `downtime_ms`), `conn.reconnect_failed` (`attempts`) |

## Product funnels

Onboarding emits `onboarding.step_viewed` / `onboarding.auth_selected` / `onboarding.completed` /
`onboarding.abandoned` (see `OnboardingViewModel`).

### Tutorial

The guided first three levels, from `GameViewModel`. Per-step rather than per-level because
tutorial drop-off is where a casual puzzle game bleeds installs, and "they quit" is not an
actionable finding — "they quit on the step that asks for a double tap" is.

| Event | Attributes | Fires |
|---|---|---|
| `tutorial.step_viewed` | `step` (the `TutorialStep` name), `level_id` | Each time a coach mark comes up, including the first one as a guided level opens. A step that is skipped over because it has nothing to point at never fires |
| `tutorial.completed` | `skipped`, `last_step` | Once. `skipped=false` means they finished level 3's script; `skipped=true` means they took the way out, and `last_step` says from where. Both write `AppData.hasCompletedTutorial`, so both are the end of it |

`step` is the enum name and not an index on purpose: the curriculum will be reordered, and a
funnel keyed on position would silently start comparing two different lessons. Drop-off is
`step_viewed` counts down the sequence; the pair to watch is the two gesture steps
(`MarkSquare`, `PlaceDog`), which are the only ones a player cannot leave by tapping anywhere.

Replaying from Settings clears the flag and arms the run again, so a small number of repeat
`tutorial.completed` events per install is expected rather than a bug.

## Gameplay

Every one of these comes from `GameViewModel`. They carry `level_id` rather than a level *name*
because the level is content: the pack is regenerated when the difficulty engine changes, and a
dashboard keyed on anything else would silently start comparing two different boards.

`attempt_number` is per level and per session — it resets when a different level is opened, not
when the app restarts. It is what makes "how many tries does level 312 take" answerable without
a session join.

| Event | Attributes | Fires |
|---|---|---|
| `game.level_started` | `level_id`, `size`, `difficulty`, `attempt_number`, `mode` | Every attempt, including a retry after a loss and a jump from the level pane. Not on resume from background |
| `game.level_completed` | `level_id`, `size`, `difficulty`, `duration_ms`, `score`, `paws`, `strikes_used`, `attempt_number`, `mode` | The last dog lands. `duration_ms` is monotonic from the attempt's start, so backgrounding does not inflate it |
| `game.level_failed` | `level_id`, `duration_ms`, `dogs_placed`, `attempt_number`, `mode` | The third bone goes. `dogs_placed` is how far they got, which is the difference between "too hard" and "unlucky" |
| `daily.started` | `date`, `streak` | Today's board is opened from the card. `level_id` is deliberately absent: it is a position in the daily pool and means nothing next to a campaign id |
| `daily.completed` | `date`, `streak`, `score` | A daily clear is written. `streak` is the number *after* the write, so it is the run the player just extended |
| `daily.freeze_used` | `streak` | A rewarded ad covered a missed day |
| `game.continued` | `level_id` | A rewarded continue after a loss, board intact |
| `game.bones_refilled` | `level_id` | The standing ad offer on the board, or the refill button on the lose sheet |
| `game.booster_used` | `booster` (`sniff`/`treat`), `level_id` | A charge is actually spent |
| `game.booster_no_op` | `booster`, `level_id` | A booster was asked for and **declined to spend**, because it had nothing to show. Should be rare; a rise means the hint engine is running out of things to say earlier than it should, which is a difficulty-calibration signal and not a UI one |
| `game.booster_refilled` | `booster`, `to` | An ad topped a consumable up. `to` is the resulting holding, not the amount granted — refills never reduce, so the two differ for anyone above the floor |

`mode` is `campaign` or `daily`, and it is on every game event rather than only the daily ones
because level ids are ambiguous without it — the two packs share a number line, so `level_id: 7`
names two different boards and any query that groups by it silently mixes them.

`daily.streak_broken` is specced in SPEC §14 and **not emitted.** Nothing on the client is told
when a streak ends: the streak is folded from stored results on every read, so a broken one is
simply a smaller number next time somebody asks. Firing the event would need a remembered
"streak as of last read" to compare against, which is exactly the counter that design refuses.
The same fact is derivable server-side from the gaps between `daily.completed` events.

## Onboarding the player

| Event | Attributes | Fires |
|---|---|---|
| `tutorial.step_viewed` | `level_id`, `step` | Each coach mark in the guided run over levels 1 to 3. `step` names the lesson, not its index, so inserting one does not shift the meaning of every prior data point |
| `tutorial.completed` | `last_step`, `skipped` | Once, when the script ends or the player skips. `last_step` on a skip is the whole value of the event: it says *where* people give up, which is the only actionable thing a tutorial funnel produces |

## Monetization

`ads.result` is the one to watch. SPEC 4.2 requires that an ad failure never costs the player the
reward, so `outcome=Rewarded` with a non-null `error_kind` is the **correct** and expected
combination — a dashboard that treats it as an anomaly has the rule backwards.

| Event | Attributes | Fires |
|---|---|---|
| `ads.gate_shown` | `placement`, `is_offline` | An ad gate is reached, before any request. Paired with `ads.result` this gives the fill rate per placement without a join to the network's own reporting |
| `ads.result` | `placement`, `outcome`, `error_kind`, `latency_ms`, `reason`, `grace_levels_used` | Every gate resolves, including the ones that resolved by failing open. `latency_ms` is what tells you whether a rewarded ad is worth preloading |
| `ads.offline_block` | `placement`, `grace_levels_used` | The offline grace ran out and the block screen went up. Should be rare; a rise means the grace is too tight |
| `iap.purchase_result` | `outcome`, `error_kind` | A purchase flow ends, in any way |
| `purchase.failed` | `product_id`, `error`, `attempt`, `final` | A store call failed and is being retried. `final` marks the attempt that gave up |

### The one that pays for itself

Difficulty calibration. `difficulty` on `level_started` / `level_completed` is the tier the
deduction engine assigned, and `attempt_number`, `duration_ms` and `strikes_used` are what
players actually experienced. The engine's rating is a claim about how hard a board is to
*reason* about; these are the measurement of whether that claim holds.

It has already been wrong once. A guard bug in the tier-2 adjacency technique had 4% of boards
rated harder than they are (`decisions.md`, 2026-09-07), and nothing in the app would have
surfaced it — the pack verification only checks that the stored numbers are in range. A band
where tier 4 completes faster than tier 3 is the shape to watch for.

## Advertising and purchases

Emitted by `RealAdGate` (`:libraries:ads:impl`), `RealPaywallCoordinator` and
`RealEntitlements` (`:libraries:billing:impl`). `placement` is the id from SPEC 5.3
(`level_complete`, `continue_level`, `booster_grant`, `skip_level`, `streak_freeze`) and is
the same string `ads.rewardedPlacements` is keyed on, so a config change and its effect on
the funnel line up without a lookup table.

**There is no event for an ad the gate declined to show.** Suppressions are the normal
case — a player in the new-user grace generates one per level — and at that volume the
funnel would be mostly noise. A suppressed interstitial logs at debug with its reason and
stays out of Loki. `ads.result` with `outcome=granted_without_ad` covers the one case
where a suppression is still interesting, because it means a reward was paid for nothing.

| Event | Attributes | Fires |
|---|---|---|
| `ads.gate_shown` | `placement`, `is_offline` | A rewarded gate is entered, or an interstitial passes all three frequency gates. `is_offline` is the **device** signal (`AppState.isDeviceOffline`), not the banner one — our backend being down is not an ad-network outage. No `level_id`: the gate is called from the game and the daily and does not know which |
| `ads.result` | `placement`, `outcome`, `latency_ms`, `error_kind`, `reason`, `grace_levels_used` | Every terminal state of a gate. `outcome` is an `AdShowResult` name (`Rewarded` / `Dismissed` / `Completed` / `NoFill` / `Offline` / `NotShown` / `Failed`) **or** the synthetic `granted_without_ad`, which carries `reason` (`pro`, `ads_disabled`, `placement_disabled`, `new_user_grace`). `latency_ms` spans prepare-plus-load-plus-watch, so it is dominated by how long the player watched — read its floor, not its mean |
| `ads.offline_block` | `placement`, `grace_levels_used` | The offline grace is spent and the block screen is requested. One per gate past the grace, so a repeat count is a player stuck offline rather than a bug |
| `iap.paywall_shown` | `trigger` | An offer the coordinator **accepted** (`continue_level` / `skip_level` / `direct`), or an offline block. Refusals — capped, disabled, already Pro — emit nothing, so the ratio of this to `ads.gate_shown` is the offer rate rather than the attempt rate |
| `iap.purchase_result` | `outcome`, `error_kind` | `outcome` is the `PurchaseOutcome` class name (`Success` / `Cancelled` / `AlreadyOwned` / `Unavailable` / `Failed`); `error_kind` is present only on `Failed` and is the store's own code (`billing_6`, `storekit_2`, `purchase_pending`) |
| `iap.restore_result` | `outcome` | `Restored` / `NothingToRestore` / `Failed`. A rise in `Failed` is a store-reachability signal, not a customer-support one — it means we could not ask, and the cached entitlement was left alone |

The ad funnel is `ads.gate_shown` → `ads.result`, split by `placement` and platform.
`outcome=NoFill` is the number to watch: every one of those is a reward given away, and
SPEC 5.3 says that is the correct behaviour, so the dashboard is measuring cost rather
than a fault.

## Warn+ log forwarding (not events)

Besides events, `GrafanaLogTree` forwards plain KLog lines at Warn and above to Loki as ordinary
OTLP logs — client errors visible without waiting on a Sentry crash. Query them with

```
{service_name="sodogku-client"} | detected_level=~"warn|error"
```

These records have **no `event_name`** (that's how you tell them apart from events); they carry
`session_id`/`install_id`, the logger `tag`, and `exception_type`/`exception_message` when a
throwable was attached. Gated by `telemetry.klogForwardingEnabled` (remote config, default **on**)
and still behind the `telemetry.appEventsEnabled` kill switch + per-session sampling — flipping
the forwarding flag off never affects events.

In-app feedback is not a Loki event: `FeedbackRepository` sends it straight to Sentry via
`Telemetry.captureUserFeedback` (verbatim message, screenshots, session-log attachment).
