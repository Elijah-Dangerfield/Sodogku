# TODO queue

Work waiting to be done, one `##` section per item. A worker routine takes items
off the top; the `feedback-triage` skill puts them on. Humans can edit it by hand
too, and should.

**This is a queue, not a changelog.** When an item ships, delete its section in
the same commit as the fix. An item that stays here with a tick next to it is an
item the next worker has to read and skip.

**Not the same thing as the polish punch list** in `BUILD-PLAN.md`. That table is
the owner's hand-written list from one sitting, kept next to the plan it belongs
to. This file is the standing queue the feedback loop feeds, appended to by a
routine. Keeping them apart is deliberate: a routine appending to a table inside
a two-thousand-line planning document conflicts with every human editing it.

## Format

Every item is exactly this shape. A worker should be able to start from the
section alone, without the conversation that produced it.

```markdown
## SD-<n> [P0|P1|P2] — <one line, imperative>

**Ask:** What should be different, in the reporter's terms. Quote them when the
wording carries intent.

**Done when:** The observable condition that ends this item. Not "refactor X",
but something you could check.

**Hints:** Where to start. File paths, the symbol that owns the behavior, what
has already been ruled out. Ends with the provenance line when it came from
feedback: `Sentry <url> · session <id> · <date>`.
```

**IDs** are one flat namespace, `SD-<n>`, assigned by incrementing the highest in
the file. Flat rather than per-area because area prefixes go stale the moment the
module structure moves, and the only thing an id has to do is be stable enough to
name in a commit message.

```shell
grep -oE '\bSD-[0-9]+' docs/todos.md | grep -oE '[0-9]+' | sort -n | tail -1
```

**Priorities.** `P0` the app is broken or losing data. `P1` real, wrong behavior
a player would notice. `P2` polish, or a rare edge. Nothing is `P0` because it is
annoying.

## Items

<!-- Newest at the bottom. -->

## SD-1 [P0] — iOS hands out rewarded boosters for free, because it has no ad SDK

**Ask:** A player on iOS taps "watch an ad" for bones or sniffs, sees no ad, and
is given the reward anyway. That is the whole rewarded economy not running on
one of two platforms.

**Done when:** The Google Mobile Ads SDK is a Swift Package Manager dependency of
the `iosApp` target, the `#if canImport(GoogleMobileAds)` paths in
`Platform/AdNetwork.swift` actually compile in, and a rewarded request on the
simulator shows Google's test ad before the reward lands.

**Hints:** `apps/ios/iosApp.xcodeproj/project.pbxproj:397-406` has exactly one
`XCRemoteSwiftPackageReference`, `sentry-cocoa`, and `Package.resolved` pins only
that. Every ad path is behind `#if canImport(GoogleMobileAds)` at
`apps/ios/iosApp/Platform/AdNetwork.swift:42-47`, so the file compiles to a stub
and the shared Kotlin reads the result as a success.

Package is `https://github.com/googleads/swift-package-manager-google-mobile-ads.git`.

Constraints that are easy to get wrong: ads are **rewarded only**
(`libraries/ads/.../AdNetwork.kt:22-24` declares one `AdFormat`, so no
interstitial, banner or app-open), and monetization **fails open**, so a load or
show failure must still grant the reward and only a deliberate `Dismissed` may
withhold it. Match the Android semantics in
`libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt`.

Leave `Info.plist:30`'s test app id and `AdUnits.kt:39`'s `useTestUnits = true`
alone. Real IDs are blocked on the owner (`OWNER-TODO.md` item 7) and test units
are what you want for verifying this anyway. Work out whether iOS needs the UMP
consent equivalent of `AdMobAdNetwork.kt:134` and say what you decided.

Tapping the iOS simulator is blocked (`OWNER-TODO.md` item 12), so be explicit
about what you could not verify rather than implying a gesture was tested.

## SD-2 [P1] — The privacy policy says the app does none of what it does

**Ask:** `pages/privacy.html:37` states the app does not "use advertising or
analytics SDKs (no Google Analytics, no Facebook SDK, no ad networks)". It ships
AdMob, the UMP consent SDK, App Tracking Transparency and a Grafana Cloud pipe.
Both stores require the policy to describe collection accurately, so this blocks
submission.

**Done when:** The page describes what the code actually sends, and a human has
read and signed off on it. That second half is not yours to close.

**Hints:** Inherited template text. Evidence for each falsehood:
`libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt:134` (UMP),
`apps/ios/iosApp/Info.plist:37` (ATT),
`libraries/telemetry/impl/.../GrafanaAppEvents.kt:139-151` (Grafana).

`docs/store/data-safety.md` is the drafted input. Ground every claim in the code:
what `GrafanaAppEvents` emits, what Sentry captures in `AppTelemetry.kt`, the
redaction rules in `libraries/core/.../logging/LogRedaction.kt`, and the install
id in `AppCache.kt`. Cover the feedback panel, which can attach a screenshot and
a log tail and is opt-in.

The genuinely privacy-preserving facts are real and worth stating plainly: no
accounts, progress only on the device, no server-side record of a player.

`pages/terms.html` is generic enough to stand unless you find something false.
A formal register is right here; boilerplate that says nothing is not.

## SD-3 [P1] — A crossed-off square is invisible to a screen reader

**Ask:** Whether a square is ruled out is the most important piece of state on
the board, and nothing announces it.

**Done when:** A cell's `contentDescription` says whether it is crossed off,
manually marked, a wrong guess, or holds a dog, and `drive.py text` shows a
difference between a marked and an unmarked cell.

**Hints:** Reproduced on the emulator (`com.sodogku.debug`, 4x4 level 1) with
`python3 scripts/dev/drive.py text`. With "Cross off squares for me" off and on,
every cell read identically, e.g. "Row 1, column 3, pink", while the ON case was
visibly painted with a large white X.

The description is built in the board component under
`libraries/ui/src/commonMain/kotlin/com/sodogku/libraries/ui/components/game/`.
Relevant sets are `GameState.visibleAutoMarks`, `manualMarks`, `wrongGuesses`,
`placedCells`.

The trap: auto-marks are display-only, so this must follow `visibleAutoMarks` and
**not** `autoMarks`, or it leaks the deduction to a player who turned the assist
off. `VerifyStrings` is enforced, so new strings go in
`libraries/resources/.../values/strings.xml`.

## SD-4 [P2] — Two docs give GitHub Pages instructions that no longer exist

**Ask:** `SETUP.md:110` and `docs/release-automation.md:203` both say to set
Pages source to `main` / `/pages`. GitHub does not offer that, and
`.github/workflows/pages.yml:10` uses `actions/deploy-pages`, which requires the
**GitHub Actions** source instead.

**Done when:** Both files say GitHub Actions, and nothing else in the repo still
describes branch-folder publishing.

**Hints:** This matters more than a normal doc fix because the app's Terms and
Privacy links are hardcoded to that Pages URL
(`libraries/config/.../LegalConfigValues.kt:35,58`), so following the wrong
instruction leaves them 404ing with no error anywhere.

While in there, `docs/SPEC.md` §20 is stale in three places: SPEC:1505-1506 asks
for rewarded, interstitial, app-open and banner ad units when only rewarded
exists; SPEC:1467-1471 says the iOS icon is still the template placeholder when a
real one is in `AppIcon.appiconset`; SPEC:1554-1557 asks whether Auto Backup
should be on when it was decided off with reasoning at
`apps/compose/src/androidMain/AndroidManifest.xml:15`. SPEC:1485 also says one
bundle id for both stores, but iOS is `com.sodogku.Sodogku` and Android is
`com.sodogku`.

## SD-5 [P2] — Dead `BuildConfig` actual in `flowroutines`' jvmMain

**Ask:** `libraries/flowroutines/src/jvmMain/kotlin/com/sodogku/libraries/core/BuildConfig.jvm.kt`
looks like a leftover: it sits in `flowroutines` but declares into the
`libraries.core` package.

**Done when:** Either it is deleted and everything still builds on every target,
or there is a comment saying which target needs it and why it lives in this
module.

**Hints:** Check whether any jvm target actually resolves the `expect` through
this module before deleting. `libraries/core` has its own
`BuildConfig.android.kt` and `BuildConfig.ios.kt`, which is what makes this one
look misplaced. Verify with the full matrix, not just Android.
