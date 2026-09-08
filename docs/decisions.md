# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-09-07 — the tutorial's scrim reports taps instead of letting them through

A guided step that says "tap the lit square" needs the lit square to work while the rest of the
board is dead. The obvious build is a scrim that declines to consume taps landing inside a hole, so
the board cell underneath handles them normally. **It does not work, and it fails silently.** A
Compose `Modifier.pointerInput` node answers `sharePointerInputWithSiblings() = false`, so a
sibling the overlay covers is not in the hit path at all — declining to consume changes nothing,
because nothing else was ever going to be asked.

`FocusScrim` now takes an `onTargetTap: (FocusTargetKey) -> Unit` and reports which lit thing was
tapped; `TutorialCoachMark` maps the key back to a cell and dispatches the same `CellTapped` action
the cell would have. Double tap still works because the two taps arrive as two actions and
`GameViewModel` owns the 320ms window either way.

The alternative was a custom `PointerInputModifierNode` overriding
`sharePointerInputWithSiblings()`. It would restore the press animation on the lit cell, which the
current version loses, but it buys that with a hand-rolled node in the design system and a much
subtler failure mode. Not worth it for a lesson the player sees once.

Two smaller calls came out of the same work. `Spotlight.targetsAreLive` defaults to **off**, so the
last-bone warning keeps behaving as it did — a tap on the lit bones closes the warning rather than
opening the bone explainer behind it. And `FocusScrim` hands its `content` the **union** of the lit
rectangles rather than `first()`: the previous behaviour picked an arbitrary member of a set, which
put the auto-mark card on top of two of the three squares it was describing.

## 2026-09-07 — the tutorial's step pointer lives in the ViewModel, not in `GameState`

`state` lags `updateState` by a dispatch, so `tutorialIndex`, the script, and whether the run is
still active are plain fields on `GameViewModel`. The state only carries what the screen renders:
the current step and the squares it points at. Two consequences worth knowing:

- **Every advance is handed the board it should point at as parameters** (`level`, `placed`,
  `autoMarks`, `justMarked`). The auto-mark lesson lights the squares a placement *just* added, and
  that difference is computable only at the moment of the placement.
- **A step whose target square does not exist is skipped, not shown.** `resolveFrame` walks forward
  past any gesture step with nothing to point at. That branch is unreachable with the shipped
  levels; it exists because the failure it prevents is a scrim over an untappable board on the
  first level of the game, and a `noStepCanLeaveTheBoardUntappable` test asserting a property is
  worth more than one asserting today's fifteen steps.

`hasCompletedTutorial` is written when the level 3 script ends or on a skip, and never on the
levels before it. Which levels have already been taught is a per-ViewModel set, so losing level 1
and starting over does not replay seven coach marks, while quitting the app mid-lesson does replay
them — somebody who left halfway has not had the lesson.

## 2026-09-07 — the offline grace reads the OS, not `AppState.isOffline`

Running C8 on a device put the offline block screen up on full wifi. `AppState.isOffline`
is `!osOnline || !backendReachable`, and the dev server is not deployed yet, so every
launch was permanently "offline" as far as the ad gate could tell. SPEC 6 already said
what the rule should be — "only the former trips the grace, since ad networks are
reachable when our own server is down" — and there was simply no flow that expressed it.

`AppState` grew `isDeviceOffline`, the platform connectivity signal on its own, with a
default of `get() = isOffline` so previews and test doubles that model one signal keep
compiling. `AppStateImpl` is the only implementation with two things to tell apart.
`RealAdGate` and `OfflineBlockViewModel` are its only consumers, and they should stay
its only consumers: anything talking to *our* backend still wants the wider signal.

Worth noticing how this failed. Nothing crashed and no test could have caught it — both
flows are `StateFlow<Boolean>` with identical types and near-identical names, and the
fake in the test file had one field standing in for both. `FakeAppState` now models them
separately, which is what makes `ourOwnBackendBeingDownDoesNotSpendTheOfflineGrace`
possible at all.

## 2026-09-07 — the ad gate asks for a paywall, it does not navigate to one

`RealAdGate` lives in a library and has no business knowing a route exists. It calls
`PaywallCoordinator.requestOffer(trigger)`, which answers yes or no against
`paywall.triggers`, `paywall.sessionCap` and the entitlement; a `PaywallNavigator` in
`:features:paywall:impl` collects the resulting bus and does the navigating.

The bus has **no replay**, which makes `PaywallNavigator`'s `AutoInit` marker
load-bearing rather than a performance tweak — a request made before the collector
attaches is gone. That is the failure mode to watch for if the boot-warm set is ever
made lazy.

The alternative was `libraries/ads/impl` depending on `:features:paywall` and calling
`Router.navigate` itself. The module-boundary checker would have allowed it (it only
forbids feature-api → feature-api and non-app → impl), which is exactly why it was worth
writing down that we chose not to.

## 2026-09-07 — the paywall is offered *after* the rewarded ad, not instead of it

The ideal shape at a third strike is one sheet with two buttons: watch an ad, or buy Pro
and never see one again. That needs `GameViewModel` to ask which the player wants, and
`GameViewModel` was owned by another chunk this session. What shipped instead:
`showRewarded` requests the offer and then runs the ad exactly as before, so the sheet
lands on top once the ad closes.

It is deliberately the safe half of the trade. The reward path is untouched, so nothing
about the offer can withhold a reward, and `paywall.sessionCap` already stops it becoming
a nag. When the game layer is free, the better version is a choice sheet in
`GameViewModel` that calls `paywallCoordinator.requestOffer` on one branch and
`adGate.showRewarded` on the other — the coordinator API already supports it.

## 2026-09-07 — ad frequency state gets its own cache; the entitlement stays in `AppData`

Two persisted things landed in C8 and they went to different places.

The **entitlement** is one boolean in `AppData` (`isProEntitled`), where SPEC 5.2 says it
goes. It is a fact about the player, it belongs next to the other things a person would
recognise, and it is exactly the "don't roll a new cache for a single boolean" case
AGENTS.md names.

The **ad bookkeeping** is a separate `ad_state` cache. Five numbers — first launch,
last interstitial, levels since, grace start, grace spent — none of which mean anything
without the config key they are compared against. Putting machinery in the file the whole
app writes to would make `AppData` harder to read for no gain, and keeping it apart means
QA can reset the ad state without touching a player's settings.

**Per-session counters are in neither.** The interstitial ceiling and the paywall cap are
in memory, keyed on `SessionTracker.current.id`. A ceiling that survived a restart would
mean a player who force-quit twice never saw another interstitial, and one that never
reset would mean a week-long session never stopped seeing them.

## 2026-09-07 — Play Billing 8, not the 7 the spec names

SPEC 5.2 says "Play Billing 7". Play stopped accepting new releases on 7 before this was
written, so 8.3.0 shipped instead. The migration is two things and both are already in
`PlayStoreBilling`: `queryProductDetailsAsync` hands back a `QueryProductDetailsResult`
rather than a bare list, and `enablePendingPurchases(PendingPurchasesParams)` is
mandatory. `enableAutoServiceReconnection()` is on, which removes the reconnect loop
every Billing 5-era implementation had to hand-roll.

The one thing not to copy from a Billing tutorial: `BILLING_UNAVAILABLE` is **not**
mapped to "not owned". It means this device cannot do billing at all, and a device that
cannot ask is not a device that answered no. Same for `SERVICE_*` and `NETWORK_ERROR`.
All of them return `StoreOwnership.Unknown`, which is the value that leaves a paying
customer alone.

## 2026-09-07 — every ad id is in one file behind one boolean

`AdUnits.useTestUnits` is the whole migration. Google's published sample units are
committed for both platforms and the `Live` blocks are empty; when a live id is missing
the accessor falls back to the test unit rather than requesting an empty string, because
a blank unit id is an SDK error and an SDK error is one more way for an ad to "fail" —
and this app *pays the player* when ads fail. A typo would quietly hand out free rewards.

The AdMob **app** id cannot live there: both SDKs read it before any app code runs, so it
is in `AndroidManifest.xml` (`com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist`
(`GADApplicationIdentifier`). That split is the one thing about this file that will catch
somebody out, which is why both ends say so in a comment.

## 2026-09-07 — the iOS ad network is compile-guarded, the iOS store is not

`IOSStoreBilling` is unguarded and real: StoreKit ships with the OS. `IOSAdNetwork` is
wrapped in `#if canImport(GoogleMobileAds)`, because the SDK arrives through SPM in Xcode
and nothing in this repo can add a package dependency to a project it cannot open. Until
somebody does, the guard makes every ad "fail", and the shared Kotlin grants the reward
anyway — correct behaviour, zero revenue, and an app that still builds.

The alternative was to commit the Swift unguarded and leave the iOS target broken until
the package is added. A repo where the iOS app does not compile is a repo where nobody
notices the next thing that breaks it.

## 2026-09-07 — badge copy lives in `:features:achievements` (the api module), not its impl

The catalog carries stable ids and no words; an exhaustive `when (AchievementId)` in the UI is
what makes adding a badge fail to compile until somebody writes them. That `when` had an obvious
home in `:features:achievements:impl` next to the grid, and it is the wrong one: the unlock toast
fires on the **win sheet**, in `:features:game:impl`, and a feature impl may only depend on
another feature's *api*. Putting `AchievementCopy` in the impl would have forced a second copy of
the mapping in the game — two exhaustive `when`s over the same enum, one of which nobody would
remember to extend.

So `AchievementCopy` is in the api module, alongside the route, and returns `StringResource`
rather than `String` — non-composable, so it is usable from a `remember` or a preview and testable
without a Compose harness. The glyphs are Kotlin constants rather than string resources: there is
nothing in an emoji to translate, and 21 more rows in `strings.xml` is 21 more things for a
translator to skip past.

## 2026-09-07 — the share sheet is a composition local, not a ViewModel dependency

`ShareText.format` is handed a title, a streak line and a footer that are **already localised** —
that is the whole reason `:libraries:sharing` holds no English. The consequence is that the only
thing in the app that can build a share is a composable, because `stringResource` is the only way
to resolve the words. A `ShareLauncher` injected into `GameViewModel` would therefore have to be
handed a string the composable built and passed back down through an action, which is a round trip
for no gain.

`LocalShareSheet` (in `:libraries:ui`) provides the launcher to the whole tree from `App.kt`,
defaulting to a no-op so previews and tests get something harmless. `ShareButton` in the design
system does the formatting, so a screen supplies only the two things it alone knows: what the board
was, and what to call it. SPEC §9 wants three share points (win sheet, daily card, level-map
long-press) and this is what keeps the second and third from being a copy of the first.

**`:libraries:sharing` keeps its empty dependency list.** `ShareLauncher` returns `Unit` rather
than `Catching<Unit>` — unlike `WebLinkLauncher`, which it otherwise mirrors — so the interface
needs nothing from `:libraries:core`. That is not only bookkeeping: a web link that fails to open
leaves the player on an unchanged screen and needs an answer, while a share sheet that fails to
present has no recovery a caller could offer. The platform impls log and move on.

## 2026-09-07 — the achievements toggle hides the row that opens the grid, not the grid's contents

SPEC §8 says the toggle "suppresses toasts and hides the tab". There is no tab, so the question was
what "hidden" means for a Settings row and a screen. Turning badges off removes the **Achievements
row** from Settings; the toggle itself stays, or there would be no way back. The screen keeps an
"achievements are off" panel behind it, which is what a deep link or a stale back stack lands on,
and that panel says in as many words that recording carried on — because the reading that stops
somebody switching them back on is the one where turning them off threw the history away.

The toggle writes exactly one boolean into `AppData` and touches nothing else. `SettingsViewModel`
has no `AchievementsRepository` at all, and the test asserts the whole of `AppData` afterwards
rather than the one field, so a second effect added later fails rather than passing quietly.

## 2026-09-07 — nothing in the app navigates to `SettingsRoute`

Found while verifying C10 on a device, and it predates this chunk: `:features:settings` builds its
graph and `SettingsScreen` works, but no `router.navigate(SettingsRoute())` exists anywhere. The
board's gear icon opens `GameDialog.Settings`, an in-place sheet with three toggles, which is a
different and much smaller surface. The full settings page — and therefore the achievements grid
hanging off it — is currently unreachable in a shipped build.

Verification for this chunk was done by pointing the start destination at `SettingsRoute()`
temporarily, driving the device, and reverting. The real fix is one navigation call from the
board's settings sheet into `SettingsRoute`, and it belongs to whoever owns `:features:game:impl`
next; it is written up here so it is not rediscovered a third time.

---

## 2026-09-07 — the pack is a route argument, and it is a `Boolean`

**Decision:** `GameRoute(levelId: Int, daily: Boolean = false)`. Which pack a board comes from is
carried by the route, not by a mode the ViewModel can be switched into, and it is a plain
`Boolean` rather than the `PackKind` enum that already exists.

**Why the route and not a mode:** the route is what a process death restores. A screen that had
been switched into daily mode in place would come back as whatever level id the route still held,
in the campaign — the player force-quits mid-daily and reopens onto a different board, with their
attempt lost and the day still open.

**Why a Boolean:** an enum route argument has to be `@Serializable` *and* registered in a typeMap
at every registration site, and forgetting either crashes graph construction on iOS with a message
that names a different argument. That landmine is already recorded twice in `AGENTS.md`. Two packs
do not need a type to tell them apart, and a third would be the moment to pay for the enum.

**The daily ignores the route's `levelId`.** It resolves the board from `DailyRepository.status()`
instead, because the board and the date the result is written against have to come from one
snapshot of the clock. The route's id came from a card drawn at some earlier moment; trusting it
would let a screen opened a second before midnight record yesterday's board against today.

## 2026-09-07 — a lost daily is spent when the player walks away, not when the bones run out

**The constraint:** `daily_result` takes one row per date and never updates it (that insert-only
rule is what makes the one-attempt lock hold whatever the clock says), and SPEC §2 allows a failed
daily to be continued with a rewarded ad exactly like a campaign level. Those two together mean
the result cannot be written at the moment the third bone goes: the revive's clear would then find
the day already locked to a loss, and the player would watch an ad, finish the board, and get
nothing.

**Decision:** `onFailed` is written when the player leaves the loss sheet. A win writes
`onCompleted` immediately, as it always could. Starting over is removed from the daily's loss
sheet entirely — a second run at a board whose score commits to a date is the replay the
one-attempt rule exists to stop — but both ad revives stay.

**The hole this leaves:** force-quitting at the loss sheet leaves the day unwritten and therefore
still playable, so a determined player can retry today's board until they win it. That is
deliberate, and it is the same call already recorded under "what the daily defends against a moved
clock": the daily is device-local with no leaderboard, every defence costs an honest player
something, and someone who does this has only cheated themselves. Closing it properly means either
an upgradeable row (giving up the insert-only lock) or writing the loss immediately (giving up the
revive), and both are worse.

## 2026-09-07 — the shared level-id number line is a progress bug, not a lookup bug

**What it looks like:** `LevelPacks.campaign.byId(7)` and `LevelPacks.daily.byId(7)` are different
boards. The obvious consequence — resolve against the right pack — is one line. The dangerous
consequence is everywhere a level id is *stored* or *compared* as campaign progress:

- `progress.onAttemptStarted(id)` and `progress.onCompleted(id)` would unlock campaign levels the
  player has never seen, and clearing the daily would hand out campaign level `id + 1`.
- `progress.record(id)`, read for `isFirstClear` and `previousBestPaws`, would answer with a
  campaign level's history.
- `maxOf(unlockedThrough, level.id)` in `startAttempt` and `loadRecords` would widen the drawer's
  frontier to the daily's id — a 264 in a pool of 730 unlocks most of the campaign.
- The drawer scrolled to `currentLevelId - 1` and highlighted `level.id == currentLevelId`, which
  on the daily meant scrolling the campaign list to a locked stranger and calling it current.

**Decision:** the daily touches `ProgressRepository` for reads that are genuinely about the
campaign (`unlockedThrough`, for the drawer) and for nothing else. `campaignFrontier()` is the one
place that decides whether the level on screen counts as progress, and `LevelDrawer.currentLevelId`
is nullable so "the board on screen is not a campaign level" is representable rather than faked
with a number that means something else.

The last one is worth the emphasis: it was invisible in tests and obvious in two seconds on a
device. Every other item here is asserted by a ViewModel test that fails when the guard is removed.

## 2026-09-07 — every freeze outcome gets a sentence, including the one that means "no"

`FreezeResult` has four branches and the repository call can also throw, so the UI has five
answers to give. All five are a dialog with a title and a line of copy.

`Declined` is the reason this is not a `when` with three empty arms. It is what a player who
opened a rewarded ad and closed it early gets, and it is the outcome most likely to be read as the
app breaking: the ad disappears, the streak does not move, and nothing says why. A thrown
repository call maps to its own "not right now" rather than to `NothingToFreeze`, because telling
someone there is nothing to freeze when the database just failed is a lie they cannot act on.

## 2026-09-07 — the config registry drift guard is a test in `:apps:integration`, not a generator

`apps/admin/config-manifest-registry.json` is a hand-written transcription of the
`ConfiguredValue` classes, and until now nothing checked it. The obvious fix is a
Gradle task that *generates* it, which would make drift impossible rather than
merely detectable. It was rejected on cost: generating means running
`:libraries:config` code on the JVM, and that module has only Android and iOS
targets. Adding a `jvm()` target to it — and to `:libraries:core` and
`:libraries:flowroutines` underneath it — purely to feed codegen is a much larger
change than reading the same classes from a test that already has them on its
classpath. The test prints the exact JSON line to paste, so the ergonomics land
in the same place.

It lives in `:apps:integration` rather than in `:libraries:config` for a reason
that only shows up at the end: it is the only test module that can see **both**
halves of the declared key set. The `telemetry.*` values are declared in
`:libraries:telemetry:impl`, which `:libraries:config` may not depend on, so a
test there would have had to pin their paths and defaults as literals — the same
hand-maintenance it exists to remove. `:apps:integration` is an `:apps:*` module
and may depend on impls, so it reads them from the real classes. It also happens
to be the module `apps/admin/build.gradle.kts` named as the intended home when
this gap was first written down.

The residual seam is unchanged and worth restating: neither `SodogkuConfigValues.all`
nor the test's three telemetry classes come from the DI graph's
`Set<QaConfigValue>`, so a value contributed to DI and added to neither list is
still invisible. No unit test can close that one without an app.

**The non-obvious half is the Gradle wiring, not the Kotlin.** A file read at
test *runtime* is invisible to the up-to-date check, so editing the registry
alone left the test task `UP-TO-DATE` and the drift shipped. This was observed,
not theorised — the first mutation run reported BUILD SUCCESSFUL against a
registry with a key deleted and three values wrong. `inputs.file(registry)` on
the `Test` tasks in both `:apps:integration` and `:apps:server` is what makes the
guard real.

## 2026-09-07 — composite `JsonConfigValue` flags belong in the manifest after all

The registry deliberately omitted them, on the grounds that they aren't targeted
per version or locale and their defaults are large. Neither holds here:
`ads.rewardedPlacements` is a four-entry map and `paywall.triggers` a three-entry
list, and the console's "what did v1.0.1 ship with" view was quietly answering
that question wrong by two keys. The schema still can't type-check them beyond
"some JSON" — `ShippedConfigSchemaTest` pins exactly which keys are in that
state, so a third one can't join them silently — but an unparseable value there
is caught by `JsonConfigValue`'s decode fallback, which is a fail-open path.

## 2026-09-07 — the console warns; the server does not refuse

SPEC 4.2 says monetization keys fail open, and the admin API can write values
that run that backwards: `ads.failureMode = "LOCK"`, `ads.offlineGraceLevels = 0`,
a rewarded placement switched off. The server could reject those outright. It
doesn't, because SPEC 4.2 is explicit that tightening in *config* is a live-ops
decision and it is the shipped **defaults** that must fail open — a server that
refuses the harsher arm also refuses the A/B test the key exists for.

So the guard is a confirm sheet (`dangerousWarning`), which on prod also makes
the operator type the environment name. That friction is the reason the list is
short. Raising `paywall.sessionCap` or dropping `ads.interstitialEveryNLevels` to
1 earns nothing: warning about ordinary retuning would only teach the operator to
type the environment name without reading the sentence above it.

## 2026-09-07 — a mistyped boolean does not fall back, it becomes `false`

Found while working out what the server's type check is actually protecting.
`getValueRecursive` resolves a boolean as `rawValue.toString().toBoolean()`, and
`"banana".toBoolean()` is `false`. So a string on `daily.enabled` or
`features.sharing` does not resolve to the shipped default and does not log — it
turns the feature off on every device that fetches it. Numeric keys are luckier:
an unparseable number resolves to null and falls back.

This is why the registry being incomplete was not a cosmetic problem.
`ConfigSchema` waves through any path the uploaded manifest doesn't mention, so
until now every `ads.*`, `daily.*`, `paywall.*` and `features.*` key was one
mistyped admin write away from being off for everyone.

## 2026-09-07 — `Cache.update` was a read-then-write, and `AppData` has several writers

**The bug:** `Cache.update`'s interface default is `set(transform(get()))`. Two callers that
overlap each transform a snapshot the other has already replaced, so the second write silently
reverts the first. `AppData` is one record shared by every toggle, counter and boot-time field, and
on a cold start the install-id minter, the review coordinator and the navigation tracker are all
writing it while the first screen is already interactive.

**How it looked from the outside:** flip a setting on a fresh install, send a piece of feedback,
relaunch — the toggle is off again, `feedbacksGiven` is 0, and `screenVisits` is empty. Three
unrelated features losing a write at once, which reads like the file never got saved rather than
like a race.

**Why nothing caught it.** Every unit test for a toggle uses a single-writer in-memory fake, where
the interleaving cannot occur; the tests are correct and will stay green through the bug. And
Sodogku had never navigated to a `TrackableRoute` — every reachable route was a plain `Route` — so
the navigation tracker's `incrementVisit` write had never fired against a live screen. Adding the
first tracked route is what made a latent race frequent enough to see.

**Fix:** both implementations override `update` atomically (`DataStore.updateData` already
serialises the read and the write; `InMemoryCache` uses `MutableStateFlow.update`), and the
interface default carries the reason so the next implementation does not quietly inherit it. The
alternative — auditing call sites to avoid concurrent writes — was rejected because the writers are
in different modules and none of them can know about the others.

**This is a template bug**, not a Sodogku one. It belongs in `docs/PORT-CANDIDATES.md` upstream.

## 2026-09-07 — Settings is a screen, and the feedback page moved to it — but its route did not

**Decision:** `:features:settings` owns the settings screen and the feedback page. `FeedbackRoute`
stays declared in `:features:home`, and `SettingsFeatureEntryPoint` registers a route from another
feature's api module.

**Why the split is temporary and deliberate:** `:features:game:impl` navigates to
`com.sodogku.features.home.FeedbackRoute`, and game code was locked for editing while C11 landed.
Moving the class would have broken a module I could not fix, so the screen moved and the route
did not. Registering it from settings is still better than the alternatives — leaving the screen in
`:features:home:impl` splits the C11 surface across two features, and declaring a *second* feedback
route ships two feedback pages. The fix is one import in `GameFeatureEntryPoint.kt` whenever game
code is next open.

**Why a screen and not a bottom sheet**, against the general guidance in `AGENTS.md`: settings is
its own context rather than a transient picker, it holds the legal links a store reviewer has to be
able to find, and "it's in settings" has to lead somewhere with a back button.

**Feedback goes to Sentry, and that is the whole backend.** `FeedbackRepository` moved to
`:libraries:sodogku` (both the settings feedback page and home's bug reporter need it, and one
`impl` may not depend on another's) and forwards to `Telemetry.captureUserFeedback`, which mints a
carrier event and attaches the note, the build, the commit and the buffered session log. There is no
Sodogku-owned inbox and, under the no-accounts rule, there will not be one. **A send is never
reported as failed** — the note is already logged locally, and telling someone their thank-you note
bounced only invites them to retype it into the same void.

## 2026-09-07 — The streak is folded from `daily_result`, never counted

**Decision:** there is no stored streak. `DailyRepository` walks the stored results backwards from
the player's current local date every time it is asked, and `AppData` gained none of the three
fields section 13.4 had penciled in for this (`dailyStreak`, `lastDailyDate`,
`freezesUsedThisMonth`). All three are derivable, and a derived number cannot drift.

**Why it matters more here than it looks:** a counter has no witness. If a bug, a crash between
two writes, or a clock jump leaves it one too low, nothing on the device can tell — the player
reports a number we can neither verify nor rebuild, and progress is device-local so there is no
server copy to fall back on. Folding makes every past bug retroactively fixable: ship the fix and
the number is right on the next read.

**The cost is one full table read per status.** A few hundred rows after several years, on a query
with no joins. A partial "recent tail" query would be a cache, and the cache is the thing this
avoids.

**Rules the fold encodes**, each pinned by a test that names an exact number rather than "not
broken" (a fold that always returned zero satisfies "a missed day breaks the streak"):

- Today does not have to be done yet. A run through yesterday counts all day today, including
  after today has been played and lost — the day is spent, but the run breaks at midnight, not on
  the loss.
- A frozen day **bridges without counting**. The streak is days you played; an ad is not one.
- A failed day is not a missed day, so no freeze may cover it. Losing has to mean something or
  the bones on the daily are decoration.
- Results dated after today are invisible to the walk. That is what a clock set forward and back
  leaves behind, and they simply wait for the date to reach them.

## 2026-09-07 — What the daily defends against a moved clock: corruption, not cheating

**Decision:** nothing in the daily tries to detect or punish clock manipulation. Selection is the
local date, results are keyed on the local date, the streak is folded from them, and a player who
moves their clock gets a self-consistent answer for whatever date they claim it is.

**Why not defend:** the daily is device-local with no leaderboard, so a time traveller only cheats
themselves — the spec said as much in section 2 before any of this was built. Every defence that
was considered costs a legitimate player something real:

- *Refuse to write a date older than the newest row* — breaks flying west, which genuinely returns
  the player to yesterday's date, and breaks an attempt that starts at 23:58 and finishes at 00:01.
- *Refuse a date in the future* — the future is only knowable relative to a clock we already do not
  trust, and the check fires on exactly the reading it cannot verify.
- *Keep a monotonic "highest date seen"* — a stored counter again, and this one bricks the daily
  for anyone whose device shipped with a bad clock that later corrected itself.

**What is defended, and is tested:** that nothing a clock does can leave a *permanently* wrong
state. Rows are only ever inserted, never updated or deleted, so a clock moved forwards and back
hides rows and then reveals them again. The streak follows the date and comes back whole. A day
that already has a result stays locked whichever direction the clock moved, so no clock setting
buys a second attempt at a board.

`DailyRepository.onCompleted` takes the **date** rather than reading "now", for the 23:58 case: the
result belongs to the board that was played, and the new day is left genuinely unplayed. Being
generous there is cheaper than explaining why a puzzle finished at one minute past midnight
counted for neither day.

## 2026-09-07 — Timezone changes are a date question, not a special case

**Decision:** `dayOf(now, zone)` and `untilNextDay(now, zone)` are free functions taking both
arguments, and `DeviceTimeZone` is a seam re-read on every call rather than a `TimeZone` resolved
once in the DI graph.

**Why the seam:** the zone changes while the app is running. A value captured at graph construction
serves a player who has just landed their old midnight — right through their first day in the new
place, which is exactly the day they are most likely to be off routine and lose a streak.

**What falls out of it**, and is what makes the behaviour testable at all: flying east can skip a
calendar date, so the streak breaks — and the freeze offer covers precisely that day, which is what
a freeze is for. Flying west repeats a date; the earlier day's board is already solved so it does
not reopen, and the streak reads one lower until the date catches up. Both are one test each with a
fake zone, no device and no clock skew.

`untilNextDay` goes through `atStartOfDayIn` rather than adding 24 hours, because a DST
spring-forward day is 23 hours long and in a few zones midnight does not exist at all on the
transition day. It is floored at a second so a strange clock cannot turn the rollover flow into a
busy loop.

## 2026-09-07 — `daily_result` is insert-only, and the primary key is the lock

**Decision:** the one-attempt-per-day rule is `@Insert(onConflict = IGNORE)` against a table whose
primary key is the local date. There is no update path on the table at all.

**Why the database and not a check:** "read the row, write if absent" is two statements with a race
between them, and the caller who forgets the read gets a silent overwrite of a better score. The
conflict strategy makes the first result the only one the database will accept, so the rule holds
even for a caller that does not know about it.

`outcome` is a stored enum name (`Completed` / `Failed` / `Frozen`) rather than the `completed` and
`froze` booleans section 13.2 sketched. Two booleans describe four states and one of them —
completed *and* frozen — is meaningless. Stored by name, not ordinal, for the reason
`level_progress.state` is.

**Related, and unresolved:** `AppDatabase` still builds with `fallbackToDestructiveMigration`. It
was harmless when the only table was an example; it now means a schema bump silently deletes every
campaign record and every streak, on a device that has no server copy of either. Fixing it needs
real migrations and belongs to the chunk before the first store release, not to C6.

## 2026-09-07 — Both daily flags are read, so neither becomes a switch nothing listens to

**Decision:** `DailyStatus.enabled` is `daily.enabled && features.dailyChallenge`.

The config has carried both since C7's key set was written — a kill switch for a broken or
offensive board, and a feature flag for rollout. Reading only one would have left the other in the
admin console looking operable, which is the failure that had already happened once with
`app.minSupportedVersion` (see the entry below). Two flags that both mean "off" cost nothing to AND
together, and a flag nobody reads costs an emergency.

## 2026-09-07 — Achievements store the facts, not the counters

**Decision:** `achievement_fact` holds one append-only row per finished attempt, and every counter
an achievement is judged on is folded back out of that log on demand. Nothing materializes a
counter. `achievement_unlock` stores only what has already been *announced*.

**Why:** two properties fall out that a counters table cannot have. An achievement added in a later
release back-fills from a player's history — the next attempt they finish grants it, dated to the
attempt that really earned it, instead of starting them at zero. And there is exactly one
representation of progress, so no cached number can disagree with the history it came from. Cards
went the same way for a different reason (its server had to re-derive counters a reinstalled client
could not be trusted to report); here there is no server, and the argument that survives is
back-fill plus no drift.

**Cost accepted:** every read re-folds the whole log, O(n) in attempts. A completionist's log is
500 campaign rows plus one a day, and the fold is arithmetic over a list — if it ever matters,
materializing counters behind `AchievementsRepository` is invisible to callers.

**The idempotency comes from the store, not the fold.** The engine cannot double-*grant* (an id
already unlocked is filtered before anything is announced) but it would happily double-*count* the
same result twice. That is why a fact carries a key and the table has a unique index on it: record
the same attempt ten times and the counters move once. Recording is written insert-first,
fold-after, so the fold only ever sees what the index let through.

## 2026-09-07 — A share cannot leak the answer because it is never handed one

**Decision:** `:libraries:sharing` takes a size, a region layout and four numbers. There is no
parameter for the solution and no dependency on `:libraries:puzzle`, so the formatter cannot print
the placements the way a screenshot would.

**Why the signature rather than a rule:** "don't include the dogs" is the sort of thing that holds
until someone adds one field to a win-sheet payload. Making it unrepresentable costs nothing — the
grid is the region partition every player sees before their first move, which is exactly what makes
a daily share worth posting.

**The test that can actually fail** is not "two solutions share identically" (that one is true by
construction and stands as a regression guard). It is `everyCellOfARegionRendersTheSameSquare`,
which fails the moment any cell is rendered differently from its neighbours in the same region —
the shape a "show where the dogs went" implementation would take.

**Discovered constraint:** Unicode has exactly nine coloured-or-neutral square emoji
(🟥🟧🟨🟩🟦🟪🟫⬜⬛) and the 10x10 band needs ten regions. Region 10 is `🔲`, the closest pair in the
set. Repeating a square instead would merge two regions into one shape on the biggest boards, which
is worse than a slightly awkward tenth glyph.

**The words are the caller's.** Title, streak line and footer are passed in already localised. The
module holds no English, does no date formatting, and cannot be the reason a share is in the wrong
language. Score grouping is a plain comma — common Kotlin has no locale-aware number formatter, and
threading a separator through the API for a string nobody parses buys less than it costs.

## 2026-09-07 — The achievement catalog carries ids, not copy

**Decision:** `Achievement` is an id, a stat, a target and a hidden flag. No name, no description,
not even a string resource key.

**Alternative considered:** `nameKey: String = "achievement_top_dog_name"`, which is what the Cards
registry does in spirit. Rejected because a typo or a forgotten string resolves to null at runtime,
in the release build, as a badge captioned with its own key. With ids only, the UI maps them in an
exhaustive `when`, so adding an achievement fails to compile until someone writes the words.

**Related:** every criterion is `counter >= target`, replacing the sealed `Criterion` hierarchy the
Cards catalog uses. Anything that cannot be phrased that way becomes a new `Stat` in the fold. One
place reasons about what counts, and progress-toward-unlock is a division instead of a per-criterion
special case.

## 2026-09-07 — Three consumables, one shape

**Decision:** Bones, Sniffs and Treats all start at 3, all refill to 3 for a rewarded ad, and all
may be *held* above 3 from level rewards. Sniff is the hint (rules squares out); Treat is a free
correct placement.

**Why one shape:** three different economies would be three things to learn before the puzzle. The
refill is capped, the holding is not — so clearing levels grows a stash, and the store of them
reads as a reward for playing rather than a meter that only ever empties. A refill never *reduces*
a holding, which is the bug a naive `count = 3` would ship.

**First tap of a booster always explains it**, whatever the count, and every tap explains once the
count is zero. Spending a consumable is irreversible, so an unfamiliar button says what it costs
before it costs anything. After that a tap just works.

**A booster is never spent for nothing.** Found on device: on an easy board the deduction engine
solves by placement alone and announces no eliminations, so the first `ruledOutCells` returned an
empty list and a sniff was consumed with no visible effect. It now reports every square that
*became* ruled out however the engine got there, and the ViewModel refuses to spend when there is
nothing to show.

## 2026-09-07 — `:libraries:progress` puts its Room entity in the api module

**Decision:** the `@Entity` and `@Dao` for `level_progress` live in `:libraries:progress` (api),
not in its `impl`.

**Why:** the shared `AppDatabase` lives in `:libraries:storage:impl`, and the module-boundary rule
forbids one impl depending on another. The entity has to be visible to the database, so it goes in
the api module — the same reason the template's own `ExampleUserDataEntity` sits in
`:libraries:sodogku:storage`. The alternative was giving progress a second database.

**Also worth knowing:** `moduleConfig.storage()` alone does *not* give a module Room on iOS. It
wires the plugin and KSP and adds `:libraries:storage` to the project-level `implementation`
configuration, which the Android target picks up and the Kotlin/Native target does not. Android
compiles clean and `compileKotlinIosSimulatorArm64` fails with unresolved `androidx.room`. The fix
is an explicit `implementation(projects.libraries.storage)` in `commonMain.dependencies`.

**Level state is ranked, not ordered by the enum.** `Locked < Unlocked < Skipped < Completed`, and
state only ever moves up. That one rule gives "never regresses", "a replay is not a downgrade",
"skipping a cleared level is a no-op" and "clearing a skipped level promotes it". The ranking is
deliberately *not* the enum declaration order, so nothing outside the impl should read an ordering
off the enum. `state` is persisted by name, not ordinal, so reordering the enum cannot silently
reinterpret a saved campaign.

## 2026-09-07 — Dog animation ships as a sprite sheet, reversing the earlier call

**Decision:** `dog_look` is packed into a 30-frame sprite sheet at board resolution
(`scripts/build_dog_sprites.py`) and stepped in Compose. A placed dog looks around and blinks.

**This reverses the C3a decision** that the clips should stay archived. That call rested on two
claims, and one of them was wrong:

- *"Animated WebP does not play on Compose Multiplatform iOS."* Still true, and still the reason
  the source clips are not shipped directly.
- *"A clip cannot go on the board — ten of them is an out-of-memory crash."* Wrong, because it
  assumed one decode per cell. At most `N` dogs are ever placed, they all render the same
  animation, and a sheet is decoded **once** and shared. Packed at 128px and subsampled to 30
  frames it is 260KB, not 60MB.

The earlier note also recorded that Pillow could not read the clips. That was a bad feature probe
on my part (`features.check("webp_anim")` is not a real feature name); Pillow reads all 60 frames
fine.

**Cost:** the sheet's frame count and grid are duplicated between the script's arguments and
`AnimatedDog.kt`'s constants. A mismatch shows up as a visibly wrong animation rather than a build
failure, which is why both are documented in SPEC section 16a.

## 2026-09-07 — Haptics go through one object, not through call sites

**Decision:** `Haptics` in `:libraries:ui` maps game events (`Mark`, `Place`, `Strike`, `Win`) to
Compose feedback types, and is constructed with the player's on/off setting already applied.

**Why:** the setting has to be honoured everywhere without every feature remembering to check it,
and Compose Multiplatform exposes only two haptic types today (`TextHandleMove`, `LongPress`). The
mapping from a rich set of game moments onto that thin vocabulary is a decision worth making once,
in one place, rather than at each call site.

## 2026-09-07 — `state` lags `updateState`, so never read it back inside one action

**The landmine:** `SEAViewModel.state` reads `stateFlow.value`, and `stateFlow` is a *derived*
`stateIn` of the mutable flow. It propagates on a coroutine dispatch, so reading `state`
immediately after `updateState { }` in the same action returns the value from **before** the
update.

**Found by:** the starter dog silently not appearing in tests while working on device. Production
happened to interleave a dispatch between the two; the test scheduler did not. Same code, two
answers, neither of them reliable.

**It was not just the one site.** `place()` updated the score and then called `win()`, which
re-read `state.score` — so a level's final score could drop the points for the very placement that
won it. It looked right on device for the same accidental reason.

**Rule:** inside one action, read `state` once at the top, then either fold everything into a
single `updateState`, or pass computed values on as parameters. The lambda argument of
`updateState` *is* fresh (it reads the mutable flow), so composing writes is fine — only reading
`state` back is not.

## 2026-09-07 — Tap marks, double tap commits, and the second tap is recognised in the ViewModel

**Decision:** a single tap writes or erases the player's cross and can never cost a life. A second
tap on the same cell within 320ms commits a guess. The double-tap is detected in `GameViewModel`,
not by `detectTapGestures(onDoubleTap = ...)`.

**Why not the gesture detector:** registering `onDoubleTap` makes Compose withhold `onTap` until
the double-tap timeout expires. That puts ~300ms of lag on marking, which is the gesture a player
performs dozens of times per board. Recognising the second tap upstream lets the cross draw
instantly and convert if another tap follows.

**Alternative considered:** LinkedIn Queens cycles empty → X → queen → empty on single taps, which
has no timing window at all. Rejected because it makes "erase this cross" a three-tap operation,
and erasing is common.

**Cost accepted:** a very fast deliberate double tap on an empty cell briefly shows a cross before
the dog lands. It reads as the mark being upgraded rather than as a glitch.

**Related:** a wrong guess leaves the cell marked rather than clearing it. The player has just
proved no dog goes there, and discarding that would make a strike cost information as well as a
bone.

## 2026-09-07 — Region ink is derived from contrast, not chosen

**Decision:** `RegionStyle.ink` (the colour marks and glyphs are drawn in) is computed per fill by
comparing the WCAG contrast ratio of a dark ink and a light ink against it, rather than being
declared alongside the fill.

**Why:** hand-assigning it got three of ten wrong. Coral, green and plum all read as "dark
colours" by eye but are light enough that white ink nearly vanishes on them. The mistake was
invisible in code review and instantly obvious once the palette was rendered as a strip on a
device. A derivation removes the error class permanently, and it self-corrects if a fill is ever
retuned.

**Related:** the same render caught `BoardCell` drawing the player's X mark with
`RegionGlyph.Plus`, which is region 6's own identity glyph — under a comment asserting it was
deliberately not one of them. The mark now has a dedicated cross, and a cross is excluded from
`RegionGlyph` so the shape can only carry one meaning.

## 2026-09-07 — Poppins, not a new font file

**Decision:** the sans family points at Poppins, which the template already bundles, rather than
adding Baloo 2 or Fredoka.

**Why:** Poppins is geometric and near-circular, which gets most of the way to the rounded display
face the art direction wants, at the cost of zero new assets and no licensing step. If it needs to
be rounder, both alternatives are OFL and are a drop-in replacement in `FontFamily.kt` with nothing
else in the type scale changing.

## 2026-09-07 — Dog art ships downscaled behind a component, and the clips stay archived

**Decision:** the seven 1024px stills ship downscaled by use case (192px board poses, 512px hero
poses) behind a single `Dog(pose = DogPose.X)` component in `:libraries:ui`. The six animated WebP
loops are archived in `art/source/` and ship nowhere. No Coil dependency.

**Why the downscale:** a cell on a 10x10 grid is about 108 physical pixels. Shipping the originals
would be 8.3MB of assets and roughly 28MB of decoded bitmaps for one puzzle; downscaled it is
620KB. The `DogPose` enum makes the board-versus-hero split a compile-time choice rather than a
call-site judgement, so nobody can paint a 512px asset into a grid cell.

**Why the clips wait:** two independent blockers. One 60-frame 512px loop is about 60MB fully
decoded, so board-cell animation was never possible and has to be Compose-driven motion on a
static asset regardless. And animated WebP does not play on Compose Multiplatform iOS at all —
Coil 3's animated decoding routes through Android's `ImageDecoder`, and Skia gives you frame one.
Adding Coil now would ship an animation that works on Android and silently freezes on iOS.

**The portable fix, when we want it:** decode each clip to a build-time sprite sheet and step
frames in Compose. Identical on both platforms, one bitmap, and the frame rate becomes ours.
That is C12 work.

**No third-party image library at all right now.** Static PNGs go through Compose Resources'
`painterResource`.

## 2026-09-07 — Level packs ship as generated Kotlin, not as an asset

**Decision:** `:tools:level-generator` writes `CampaignPackData.kt` and
`DailyPackData.kt` into `:libraries:levels` as a `List<String>`, one
pipe-delimited line per level. The spec originally called for a JSON/binary
asset.

**Why:** the pack verification test is the only thing standing between an
unsolvable level and the store, and it has to run everywhere. Compose Resources
loading is suspend, platform-mediated, and awkward in Android unit tests;
generated source loads identically in a JVM test, on Android and on iOS with
nothing in the way. A malformed pack then fails compilation rather than the app.

**Cost:** 97KB of generated source in the repo, and regenerating produces a
large diff. Accepted — it is append-only data nobody reads by hand, and the
generator is deterministic per seed so an unchanged seed produces an unchanged
file.

## 2026-09-07 — Par scores and paw thresholds are derived, not baked into the pack

**Decision:** `LevelDefinition` carries only `id`, `board`, `solution` and
`difficulty`. The spec's `parScore` and `pawThresholds` fields are dropped;
those get derived at runtime from size and difficulty in `:libraries:scoring`
using coefficients from remote config.

**Why:** baked thresholds are un-tunable. What counts as a three-paw clear is
exactly the kind of number the config split (SPEC section 4) says belongs on the
server, and freezing it into the pack would mean a regenerated pack and an app
release to retune it. It also decouples C2 from C3 entirely.

## 2026-09-07 — Uniqueness comes from targeted refinement, not random mutation

**Decision:** `BoardFactory.refineToUnique` drives a board to a single solution
by repeatedly pulling a rival solution and applying the region move that kills
*that specific placement*, choosing between killers by which leaves the fewest
solutions.

**Alternatives measured, not assumed:**

- *Random mutation.* 0 unique 10x10 boards in 60 attempts, unchanged from 12
  rounds to 120. Random moves rarely invalidate any particular rival.
- *Balanced region growth* (extend the smallest region rather than a random
  frontier cell), on the theory that a 22-cell region constrains nothing.
  Measurably **worse**: 2/40 versus 4/40 at 10x10. Uneven regions constrain
  more, because a small region pins its dog tightly.

Targeted refinement converts 23/40 at 10x10, and generating all 1230 shipped
levels takes 42 seconds.

**The subtle part:** progress is deliberately not gated on the solution count
decreasing. The count is capped for speed, so a wide-open board reads the same
before and after a genuinely useful move; demanding a strict decrease stalls a
10x10 on move one. Killing the rival is the real invariant, and the seed
solution survives by construction because region moves never touch a dog cell.

## 2026-09-07 — No accounts: the identity stack is removed, not disabled

**Decision:** `:libraries:identity` (+ impl), the Supabase auth screens in
`:features:onboarding:impl`, the session-expired recovery route, the
user-scoped sync/reset machinery, and the server's `/v1/me` + player-report
surface are all deleted rather than left dormant. `AuthGate` keeps its seam in
`:libraries:core` with a new `AlwaysReadyAuthGate` default binding in
`:libraries:networking`, next to `NoOpAuthTokenProvider` and for the same
boundary reason.

**Alternatives:** leave identity in place and simply never call it. **Why
delete:** dormant auth still runs at boot — `GuestAccountCreator` and
`GuestSessionHealer` fire on the launch path and would make doomed Supabase
calls on every cold start, muddying logs and telemetry for the whole project.
And every screen built on top would have to decide whether to consult a session
that can never exist. Sodogku's only backend surface is public remote config.

**Cost accepted:** progress is device-local and cannot survive a reinstall. The
Pro entitlement still travels via store restore. If progress ever needs to move
devices, the cheap path is an export/import code (see `SPEC.md` §18), not
resurrecting accounts.

## 2026-09-07 — Rollout bucketing keys on install id, not user id

**Decision:** `AppConfigSource.read` drops its `UserId?` parameter and the
targeting engine buckets rollouts (and evaluates allow/deny lists) on
`ClientContext.installId`.

**Why:** the engine already fell back to `installId` when no user was resolved,
so this deletes a branch rather than adding one. Staged rollouts and A/B tests
on ad frequency — the main reason the config server exists — work fine keyed on
a stable per-install id, and there is no user id to key on.

**Cost:** a reinstall re-buckets that device. Acceptable for tuning ad cadence;
it would not be acceptable for a billing experiment.

## 2026-09-07 — User-facing copy goes through `:libraries:resources` from day one

**Decision:** the `VerifyStrings` detekt rule is honored rather than baselined.
`OnboardingScreen` is the worked example: strings live in
`libraries/resources/src/commonMain/composeResources/values/strings.xml` and
resolve through `stringResource(Res.string.…)`.

**Why:** the template ships the rule active but baselines every screen that
predates it, so nothing actually followed it. Sodogku will have a lot of copy
(rule chips, praise text, boosters, paywall, legal), and retrofitting string
extraction across twenty screens costs far more than writing the first one
correctly. The baseline stays for the template's leftover screens, which get
converted as each is replaced by real game UI.

## 2026-06-21 — Server mirrors client conventions

**Decision:** `:apps:server` reuses the client's stack — kotlin-inject + anvil DI
(`ServerScope`/`ServerComponent`), the `domain/` interface + `data/` impl split,
conventional commits, the version catalog. It's a plain JVM `application` module
(no convention plugin; those are KMP-only).

**Why:** one mental model across client and server. An agent (or human) moving
between them doesn't re-learn DI, error handling, or module layout. The cost —
the server can't use the KMP `:libraries:core` (`Catching`, logging) because that
module has no JVM target — was accepted; the server keeps a couple of small local
equivalents rather than forcing a `jvm()` target onto every client library.

## 2026-06-21 — Graceful degradation over required config

**Decision:** `DATABASE_URL`, `SUPABASE_URL`, `SENTRY_DSN`, and the OTLP endpoint
are all optional. With none set, the server boots and serves `/_health` +
`/v1/example`; DB-backed and authenticated routes simply aren't mounted, Sentry
no-ops, and OpenTelemetry exports to stdout.

**Alternatives:** require `DATABASE_URL` + `SUPABASE_URL` like the Cards origin
(fail-fast). **Why optional:** this is a template — "clone and run, see it boot"
beats a fail-fast error on first run. The fail-fast discipline still applies per
field via `Env.require` when a future field genuinely can't be defaulted.

## 2026-06-21 — Auth is JWKS verification, never a shared secret

**Decision:** the server verifies Supabase JWTs against the project's public keys
(JWKS / ES256). The `JwtVerification` sealed seam has `Jwks` (prod) and `Static`
(tests mint HS256 tokens against a known verifier).

**Why:** no Supabase secret ever lives on the server, and auth — the highest-risk
surface — is fully testable offline (route tests + `FullStackMeTest` run the real
validate/challenge path with no network).

## 2026-06-21 — `NoOpAuthTokenProvider` lives in the `:networking` api module

**Decision:** the default no-op `AuthTokenProvider` binding sits in
`:libraries:networking` (api), not `:impl`.

**Why:** the module-boundary rule forbids one `:impl` depending on another, but
`:libraries:identity:impl` must reference `NoOpAuthTokenProvider` to override it
with `@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`. Putting the
default binding next to the interface it defaults keeps the replacement
boundary-clean. (See also the `enforceModuleBoundaries` self-edge fix in
`build-logic`.)

## 2026-06-21 — `serverOnly` build slimming

**Decision:** `-Dsodogku.serverOnly=true` makes `settings.gradle.kts` include
only `:apps:server`, so a Docker image build needs no Android/iOS toolchain.

**Why:** this is a KMP monorepo; without slimming, a server image build would
configure every client module and need the Android SDK + Kotlin/Native. The
server has no client-library deps today, so the gate is a pure settings change;
if it gains one, add an always-included `include(...)` + a Dockerfile `COPY`.

## 2026-06-21 — Flyway SQL is the schema source of truth

**Decision:** migrations under `resources/db/migration` define the schema; the
Exposed `Tables.kt` objects are read-side projections kept honest by
`DatabaseSchemaTest`. Repositories treat a unique-violation (SQLSTATE `23505`) as
the arbiter rather than pre-checking for races.

**Why:** one procedure for schema change (add the next `V##__name.sql`, never edit
an applied one), and idempotency that's correct under concurrency.

## 2026-09-07 — the adjacency technique was half-blind, and the packs were re-rated

`adjacencyConfinement` swept rows and columns in one loop, and the guard that
skipped a resolved *row* skipped the *column* of the same index with it. Rows
resolve constantly mid-solve, so a slab of tier-2 reasoning disappeared exactly
when a board is most constrained, and the engine reached for a tier-4
contradiction where a tier-2 fact was sitting in plain sight.

Measured over 200 random unique boards: 4% were mis-rated, always *too hard*, by
up to two tiers. Split into two sweeps, one per axis. Regenerating the campaign
moved 14 levels down out of tier 4 (122 → 108) into tiers 2 and 3. Nearly half
the pack's lines changed, because difficulty feeds candidate acceptance and band
ordering, so a re-rating cascades through the generator's RNG stream.

The reason this was invisible: the baked difficulty is a cache of
`Difficulty.score`, and nothing compared the two. `LevelPackVerificationTest` only
asked whether the stored numbers were in range and non-decreasing, which stale
numbers satisfy perfectly. `everyBakedDifficultyMatchesWhatTheEngineNowSays` now
re-derives every one, so any future retiering fails the build with the command to
fix it instead of shipping a pack that describes an engine we no longer have.

`DeductionEngineTest.shallowerTechniquesAreAlwaysPreferred` could never have
caught it either: it compares `nextStep` against `nextStep(maxTier - 1)`, which
runs the same broken technique. An engine compared only against itself agrees
with itself.

## 2026-09-07 — a hint may not reason from a dead end

`ruledOutCells` checked `ruleViolations` and stopped there. Breaking no rule is
not the same as still being winnable: a partial can be legal and have zero
completions. Every technique in the engine is sound, which is precisely the
danger — reason soundly from a false premise and it will confidently cross out
the square the answer is on. It did, on 12% of legal-but-dead partials.

Both hint entry points now establish satisfiability first. `nextCell` already had
the solve and could not reach it, because `deducePlacement` returned before it;
the solve moved above. It costs under a millisecond at 10x10.

Not reachable in today's flow — a wrong guess marks the cell and costs a bone
rather than placing a dog, so `placed` only ever holds correct dogs. It is one
undo or restore-state feature away from mattering, and the KDoc already promised
the guarantee.

Related: `ruledOutCells` lost its `limit = Int.MAX_VALUE` default. Unbounded it
returns every non-dog square — 90 of 100 at 10x10 — so the convenient overload
was the one that hands over the answer by exclusion for the price of one sniff.

## 2026-09-07 — three tests that passed on `emptyList()`

The sniff-spent-for-nothing bug found on device had no regression test, and could
not have had one: all three tests covering `ruledOutCells` passed against a stub
returning an empty list. One asserted `none { it in truth }` (vacuous when
empty), one asserted `size <= 3` when the bug was size 0, and one asserted empty
on a finished board, which returns before the function body runs.

The lesson is narrower than "test more": an assertion of the form "nothing bad is
in the output" says nothing at all about an empty output, and it is the natural
shape to reach for when the property under test is soundness. Every such
assertion now has a companion that the output is non-empty.

`aHintAlwaysHasSomethingToSay` only asserts over the opening half of a board.
Late on, the auto-marks have already crossed off everything derivable — measured
at 5 of 7 placed with one candidate per open row — so there is genuinely nothing
to reveal, and the ViewModel declines to spend the sniff. Asserting a reveal
there would be asserting a lie.

## 2026-09-07 — the kill switch was pointed at a key nothing reads

The spec's config table named the force-update gate `app.minSupportedVersion`.
The admin console's kill-switch panel and its manifest registry were already
built against `upgrade.minSupportedVersionCode`, from the template. Following the
spec would have shipped an emergency control that edits a key no client reads,
and it would have looked like it worked right up until the emergency.

The client moved to the admin's namespace rather than the other way round: the
console has a live panel, a registry and server validation behind that path, and
the spec was a draft. `upgrade.maintenanceMode` came along with it, so the client
now declares the whole gate the console can already set.

The registry's `minSupportedVersionCode` default also went from 1 to 0, matching
the client's fail-open rule. Every gate in this namespace defaults to "block
nobody", because it is the only config that can brick every install at once.

## 2026-09-07 — treats start at three, like everything else

Section 4.3 of the spec said 1, section 1.5 and the "three consumables, one
shape" decision said 3, and the config landed on 1 because the table is what the
implementer was pointed at. Three is right — the whole reason the consumables
share a shape is that three different economies would be three things to learn
before the puzzle. The table was the stale copy; it now says 3.

Added `boosters.refillTo` while here. What an ad tops you up to was the one core
economy number that was a compile-time constant while everything around it was
tunable. It is a floor, never a cap: level rewards push a holding above it and a
refill leaves those alone.

## 2026-09-07 — the standing ad offer greys out rather than always firing

The ask was "an ad button that is always clickable, it just refills your bones".
It is always *there* — that is the part that matters, because otherwise nobody
discovers the offer until they have already run out and the app looks like it is
punishing them. But it greys out when bones are full.

Selling an ad for nothing is worse than not offering one. A player who taps it at
three bones, sits through thirty seconds and gets three bones back learns that the
button lies, and that is a more expensive lesson than the impression is worth. It
sits in the booster row rather than the header because that row is where a stuck
player is already looking.

`refillBones` also stopped assigning `MAX_LIVES` and now takes `maxOf`, matching
the rule the booster refill already followed: a holding earned above the floor is
never reduced by topping up.

## 2026-09-07 — placed dogs get one idle loop each and keep it

Four sprite sheets exist (`look`, `tilt`, `pant`, `flop`) and only `look` was
wired. Each placed dog now picks a loop from its cell index, so a board reads as
a row of animals rather than one animal drawn eight times.

It picks once and keeps it. Switching mid-attempt was the tempting version and is
the wrong one: motion in the periphery pulls the eye, and pulling it away from
the puzzle is the opposite of what idle animation is for. Four separate sheets
rather than one big one, because Compose caches each `imageResource` and a board
only ever shows a handful of dogs — only the loops in play get decoded.

Same reasoning retires the pop on `PawRating` in the level list. The pop means
"you just earned these", which is a lie when the rating is being recalled, and
in a 500-row list rows recycle, so every completed level twitched each time it
scrolled back on screen.

## 2026-09-07 — the game's numbers move to the Display scale

"Big and bubbly, the level especially." The level and the score were on
`Heading.H700`, which is the same weight as every section title in the app, so
they read as chrome — one more label on a screen already full of them.

They are now `Display.D900`, and the labels above them drop to `Caption.C300`.
The scale already existed and nothing in the game used it. Poppins was picked in
C3a precisely because it is geometric and near-circular; at display size the same
digits stop looking like a status bar. The label is there to say which number
this is, once. It does not need to compete.

No new font, and no new type scale. Adding either would have been the obvious
move and the wrong one — the "bubbly" was already available in the scale that
shipped, unused.

## 2026-09-07 — the win sheet's ad badge was a copy of the design-system one

`GameOutcomeSheets` had a private `AdBadge` that reimplemented `RewardBadge`
down to the same accent and radius. Two things meaning "an ad is involved" is one
too many: the whole point of a single accent for ad affordances is that a player
learns the colour once, and that only holds if there is one component to change
when it moves.

The lose sheet's refill button is now badged too, unconditionally — refilling
*always* costs an ad, unlike Next level, where one is only sometimes due.

## 2026-09-07 — blocked on art that never reached disk

Two assets the user described in chat are not in `sodogku-assets/`: the **sad dog**
for the out-of-bones sheet, and the **better bone art**. The lose sheet still uses
`DogPose.HardMode` and the bones are still drawn in `GameShapes.drawBone`. Both
call sites are one-liners once the files exist.

`dog-appmark.png` also still carries sudoku numerals, which Sodogku does not have.
It needs redrawing before store prep.

What *was* there and unused: `dog_idle.webp` and `dog_bark.webp`. `idle` is now a
fifth placed-dog loop. `bark` is a reaction rather than an idle, so it is being
kept for the win moment rather than added to the loop rotation.

## 2026-09-07 — the database stopped dropping itself

`RealAppDatabaseProvider` built with `fallbackToDestructiveMigration(dropAllTables = true)`.
Two agents flagged it independently on the same afternoon, from opposite ends of
the schema, and they were right: it was harmless while the only table was the
template's example, and it became a silent unrecoverable wipe the moment
`level_progress` landed.

There is no account. A player's campaign records, daily streak and achievements
exist in exactly one place — that file on their phone. The next release that adds
a column would have deleted all of it, on launch, with no error and nothing to
restore from.

Now: `autoMigrations` for 6→7 and 7→8, and destructive fallback narrowed to
versions 1–5, which are template history no install has ever run. Every addition
so far is a new table, so Room writes the migrations itself. The value of the
list is what happens when it *can't* — a renamed or retyped column now fails the
build rather than the player's save.

## 2026-09-07 — the lagging `state` landmine, third sighting

Wiring the achievement log found it again: `lose()` computed
`MAX_LIVES - state.livesRemaining` after the caller's `updateState` had set the
third strike, and got 2. `state` reads a derived `stateIn` flow that lags
`updateState` by a dispatch, so the read saw the board one strike ago and the
achievement log believed it.

This is the same shape as the starter-dog bug and the win-scoring bug. The rule
that keeps coming out of it: **a value that a suspend function computes and then
needs again must be passed as a parameter, never read back off `state`.** Reading
it back is always available, always compiles, and is wrong roughly half the time
depending on dispatch timing — which is why it keeps getting written.

Both `recordAttempt` call sites now take `livesRemaining` explicitly, and the
loss path passes the strike-adjusted score card rather than re-reading that too.

## 2026-09-07 — losses go in the achievement log

The log records failed attempts as well as clears. A run that ended on the last
bone is evidence about how someone plays, and some badges are about persistence
rather than success. Recording only wins would make the log a record of wins,
which is a different and much less useful thing — and it would make any future
"attempts per clear" question unanswerable from data we chose not to keep.

Consumable spends are counted per *attempt*, not per session, and reset on retry.
"Cleared it without help" is a claim about one attempt; a counter that carried
across retries would make it unearnable for anyone who ever used a hint.

## 2026-09-07 — an unparseable boolean was silently `false`

`getValueRecursive` resolved booleans with `rawValue.toString().toBoolean()`,
and `"banana".toBoolean()` is `false`. So a string typed into `ads.enabled`,
`daily.enabled` or any `features.*` flag in the admin console would have turned
that feature off on every device that fetched it — no log, no fallback, no crash.

This is the one path that could make a monetization key fail *closed*, which is
the exact guarantee SPEC 4.2 is built around, and the client-side fail-open test
could not see it: that test reads against an *empty* map, where every key already
falls back correctly. The hole was in the resolve of a value that was present and
malformed.

Booleans now resolve only from "true"/"false", case-insensitively, and anything
else is null so the declared default wins. Casing stays forgiving because the
console lets an operator type a raw value and "True" is not a mistake worth
punishing. Numeric keys were already safe — `toDoubleOrNull` returns null rather
than inventing a zero, which is exactly the shape the boolean case was missing.

The general rule this is an instance of: **a parse that invents an answer is
worse than no answer.** The default was chosen deliberately and is written down.
The invented value is whatever the coercion happened to produce.

## 2026-09-07 — the persistent cache's clear() deleted nothing

`DataStoreCache.clear()` called `deleteFile(name)` while the file is written as
`"$name.json"`, so it removed nothing. Correcting the name would not have been
enough either: `DataStore` holds the value in memory and serves it from there, so
even a successful delete would have left every reader on the old value until the
process died.

It now writes the serializer's default back through `updateData`, which is the
only write path `DataStore` observes. Nothing called `AppCache.clear()` yet, which
is why a doubly-broken method sat there unnoticed — the account-switch clearer it
was written for was deleted along with accounts in C0.

## 2026-09-07 — the settings screen was shipped and unreachable

`:features:settings` landed complete, tested and driven on a device, and nothing
in the app navigated to it. The board's gear opened `GameDialog.Settings`, an
in-place sheet that duplicated every row — including the strings. Two settings
surfaces would have drifted the first time either was edited, and the
achievements grid hangs off the screen nobody could reach, so C10's whole UI was
dead in a real build.

The gear now sends `GameAction.OpenSettings` out through an event to the router,
and `GameDialog.Settings` and `SettingsContent` are deleted. The lesson is
smaller than it looks: a feature module can be complete, tested, verified on a
device and still be dead code, and nothing in the build says so. Three separate
agents each assumed someone else owned the one line.

The daily agent found the mirror image of the same thing — the level pane scrolled
the campaign list to a *daily's* id and highlighted a locked stranger as "current",
because `currentLevelId` was not nullable and "not a campaign level" was not
representable. Both are the same failure to make an invalid state impossible.

## 2026-09-07 — three desktop source sets that were never compiled

`libraries/ui/src/jvmMain` and `libraries/navigation/impl/src/jvmMain` held a
`JvmWebLinkLauncher`, a desktop `FontFamily` and a `NativeButton` actual. Only
`:libraries:puzzle` declares a `jvm()` target — it needs one so the level
generator can run on the JVM — so none of those three files has ever been
compiled by anything.

Deleted. They read as live platform implementations, which is worse than absent:
someone fixing a link-opening bug on desktop would have edited a file that does
not run, and the build would have agreed with them by staying green.

Sodogku is Android and iOS. If a desktop target ever lands, this code is one
`git log` away and will need rewriting against whatever the desktop story is then.

## 2026-09-07 — placed dogs sit still, mostly

The user, on the idle loops: *"I prefer the idle and the look around. For sure
not the shake. Maybe occasionally or something."*

The rotation is now weighted rather than uniform — `idle` and `look` take five of
six slots and `pant` takes the sixth, so a full board has a couple of dogs doing
something slightly different and no board is a row of clones. `tilt` and `flop`
are out of the rotation entirely: they are the two that read as a shake, and a
head snapping about in peripheral vision pulls focus off the puzzle, which is the
opposite of what idle motion is for.

Both sheets stay on disk. They are the right loops for a celebration or an empty
state, where the motion *is* the point.

## 2026-09-07 — 37 config keys that nothing reads

A review found that all fourteen `scoring.*` keys are declared, in the fallback
map, in the admin registry and rendered in the console with a typed editor — and
that `GameViewModel` calls `Scoring.placement`, `complete` and `paws` letting the
`config` parameter default to `ScoringConfig.Default`. The entire scoring tuning
surface was decorative.

Rather than fix the instance, `ConfigValuesAreReadTest` now scans the source tree
for a mention of every declared value outside `:libraries:config`. It found **37**,
not 14: the whole consumable economy, all of progression, the legal gate, the
upgrade and maintenance gates, and three feature switches whose features shipped
without reading them.

This is the third time the same shape has cost us. `app.minSupportedVersion` was
the client's name for a key the console edited as
`upgrade.minSupportedVersionCode` — the kill switch, pointed at nothing. Every
check we had verified that a key was *declared consistently*, and a key can be
declared perfectly and be completely inert.

The method is a text search, and its limit is stated in the test: it proves a
class is named outside its own declaration, not that the value reaches a
decision. A value injected and then ignored still passes. That is a much smaller
hole than the one it closes, because forgetting to inject is the mistake people
actually make.

The 37 are baselined in `UNWIRED`, held by two tests: one fails if a new name
appears, the other fails if a listed name gains a reader and is left behind. The
list can only shrink.
