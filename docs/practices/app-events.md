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

## Gameplay

Every one of these comes from `GameViewModel`. They carry `level_id` rather than a level *name*
because the level is content: the pack is regenerated when the difficulty engine changes, and a
dashboard keyed on anything else would silently start comparing two different boards.

`attempt_number` is per level and per session — it resets when a different level is opened, not
when the app restarts. It is what makes "how many tries does level 312 take" answerable without
a session join.

| Event | Attributes | Fires |
|---|---|---|
| `game.level_started` | `level_id`, `size`, `difficulty`, `attempt_number` | Every attempt, including a retry after a loss and a jump from the level pane. Not on resume from background |
| `game.level_completed` | `level_id`, `size`, `difficulty`, `duration_ms`, `score`, `paws`, `strikes_used`, `attempt_number` | The last dog lands. `duration_ms` is monotonic from the attempt's start, so backgrounding does not inflate it |
| `game.level_failed` | `level_id`, `duration_ms`, `dogs_placed`, `attempt_number` | The third bone goes. `dogs_placed` is how far they got, which is the difference between "too hard" and "unlucky" |
| `game.continued` | `level_id` | A rewarded continue after a loss, board intact |
| `game.bones_refilled` | `level_id` | The standing ad offer on the board, or the refill button on the lose sheet |
| `game.booster_used` | `booster` (`sniff`/`treat`), `level_id` | A charge is actually spent |
| `game.booster_no_op` | `booster`, `level_id` | A booster was asked for and **declined to spend**, because it had nothing to show. Should be rare; a rise means the hint engine is running out of things to say earlier than it should, which is a difficulty-calibration signal and not a UI one |
| `game.booster_refilled` | `booster`, `to` | An ad topped a consumable up. `to` is the resulting holding, not the amount granted — refills never reduce, so the two differ for anyone above the floor |

### The one that pays for itself

Difficulty calibration. `difficulty` on `level_started` / `level_completed` is the tier the
deduction engine assigned, and `attempt_number`, `duration_ms` and `strikes_used` are what
players actually experienced. The engine's rating is a claim about how hard a board is to
*reason* about; these are the measurement of whether that claim holds.

It has already been wrong once. A guard bug in the tier-2 adjacency technique had 4% of boards
rated harder than they are (`decisions.md`, 2026-09-07), and nothing in the app would have
surfaced it — the pack verification only checks that the stored numbers are in range. A band
where tier 4 completes faster than tier 3 is the shape to watch for.

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
