# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

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
