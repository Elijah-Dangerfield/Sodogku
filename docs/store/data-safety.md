# Data safety and privacy nutrition labels

Answers for Google Play's **Data safety** form and Apple's **App Privacy** (nutrition label)
questionnaire, derived from what the code actually does. Every row cites the file and the
mechanism that makes it true. Where the honest answer is "not determined", it says so and says
where to look, because a guess here is a policy violation rather than a typo.

First derived 2026-09-08. **Re-derived 2026-09-10** against the tree at that date, and every
citation below was re-opened rather than carried over. **Re-derive it whenever a network call, an
SDK, or a telemetry attribute changes.** The four things most likely to invalidate a row:

- a new `logEvent` attribute (`docs/practices/app-events.md`),
- a new SDK, which now means two catalogues and not one: `gradle/libs.versions.toml` for Android
  and common code, and the Swift package list in `apps/ios/iosApp.xcodeproj/project.pbxproj` for
  iOS, where the ad SDK and Sentry actually arrive,
- a new destination for anything the player types,
- anything that starts calling `Telemetry.setUser`.

Line numbers rot faster than facts. The 2026-09-08 citations into `AppTelemetry.kt` had drifted by
up to forty lines by the time of this pass while every claim they supported was still true, so a
citation that misses by a few lines is a stale doc rather than a changed answer. Check the symbol,
not the number.

---

## What changed since the last derivation

The owner has to go and change store form fields, so the changes are listed before the rows they
sit in. **Nothing here adds or removes a Google Play data type.** Two Apple questions moved.

| # | Area | Was, on 2026-09-08 | Is now | What to do in a console |
|---|---|---|---|---|
| 1 | **Sentry** | A blank DSN. `SentryRuntimeConfig.isEnabled` was false, `Sentry.init` never ran, and every crash, diagnostic and feedback row was true of the code but false of any build. | The DSN resolves non-blank, so crash reporting, traces, breadcrumbs, sessions and feedback all actually leave the device. `TelemetryInfo.sentryDsn` reads `SodogkuBuildConfig.SENTRY_DSN`, resolved at `build-logic/.../Versioning.kt:190` from env `SENTRY_DSN` (set in `.github/workflows/beta.yml:78,197` and `release.yml:135,384`) then `local.properties` key `sentry.dsn`, which on the owner's machine is now set. | **No field changes.** The Play crash and diagnostics rows and the Apple Diagnostics rows were already answered Yes on the strength of the code. They are now true of a shipped build too, which is the state both forms assume. |
| 2 | **What a feedback report carries** | Verbatim text, build provenance, and a `session-log.txt` attachment. | The same, plus the typed message twice more: as a `feedback_message` **extra** on the carrier event (`AppTelemetry.kt:243`) and as a `feedback.txt` **attachment** (`AppTelemetry.kt:244-246`), and up to three image attachments (`AppTelemetry.kt:253-258`, cap at `:329`). | **No field changes.** Same data type, more copies of it. It does change what the privacy policy has to say, and `pages/privacy.html` already says it. |
| 3 | **Screenshots** | §2.5 said `FeedbackRepositoryImpl` passed no screenshots. Wrong even then. | It passes `screenshots: List<ByteArray>` and `includeLogs: Boolean` (`FeedbackRepository.kt:40-56`). The only caller that fills them is the tester-only panel, gated on `BuildInfo.isTesterBuild` at `DevFeedbackHost.kt:55`, which is debug **or TestFlight** (`BuildInfo.kt:35`). The frame is captured from the app's own screen (`devfeedback/ScreenshotEncoder.kt`); no camera and no photo library are involved. | **No Play field changes**: the panel is absent from a Play build. On Apple, note that TestFlight builds do carry it, so the User Content row covers a screen capture as well as text. |
| 4 | **Game Center, iOS** | Not mentioned at all. Landed 2026-09-08 in `bdac05e`, after the derivation. | On iOS the app authenticates against Game Center at startup (`RealLeaderboards.kt:89-94`) and submits lifetime score and longest streak to Apple under the player's Game Center identity (`GameCenterServices.kt:109-133`). Android has no equivalent and never will silently: `NoGameServices` reports unavailable forever. | **Apple: one question to settle before filing**, see §5 and §7.6. Play: nothing, there is no Android path. |
| 5 | **iOS ad SDK** | "iOS serves no ads at all until the Google Mobile Ads Swift package is added", with a warning not to file the label on that basis. | The package is in the Xcode project (`project.pbxproj:410-417`, product at `:428-429`), alongside `sentry-cocoa` (`:402-409`, `:423-424`). Ads and the IDFA are live on iOS. | **File the Apple advertising rows as true**, including Used for Tracking. The escape hatch the old §2.2 described is gone. |
| 6 | **Camera permission** | §2.10 and §7.2 said `android.permission.CAMERA` was declared and would show on the Play listing. | It was not, and is not. `apps/compose/src/androidMain/AndroidManifest.xml` declares **no** `uses-permission` at all, and no other manifest in the tree does either. The merged set comes entirely from bundled libraries. | **Nothing to remove.** If a listing draft was written expecting a camera line, it does not appear. |
| 7 | **Streak storage** | The streak was folded out of `daily_result`. | A new local table, `play_day`, one row per local date the player finished any board (`PlayDayDao.kt:35`), carried by database version 9 (`AppDatabase.kt:22-30`). Device-local, no payload beyond the date. | **No field changes.** It never leaves the device. |
| 8 | **Telemetry attributes** | The registry as it stood. | `game.level_completed` now carries `strikes_used` (wrong guesses this attempt) and `paws` (a five-paw rating), plus `sniffs_used` / `treats_used` / `auto_mark` (`GameViewModel.kt:1549-1563`). New event `leaderboard.submitted` with `board` and `value` (`RealLeaderboards.kt:156-160`). | **No field changes.** All of it is gameplay measurement under App activity and Product Interaction, which were already declared. |
| 9 | **Restore purchases** | Filed as a finding: no reachable restore control, an App Review risk. | Settings has one, always present whether or not the player is Pro (`SettingsScreen.kt:201-205`). | **Nothing for a form.** Closed in §8. |
| 10 | **`setUser` and accounts** | No accounts, `setUser` never called. | **Unchanged, and re-verified.** See §1. | Nothing. |

---

## 1. The two facts that shape every answer

**There are no accounts of ours, and identity was deleted rather than disabled.**
`:libraries:identity`, the Supabase auth screens and the `UserScopedSyncer` / `UserScopedDataReset`
machinery were removed in C0 (`AGENTS.md`, "No accounts"; `docs/decisions.md`). The client has no
sign-in, no user id, no server-side user record, and `apps/server` has no auth plugin and no user
tables. So:

- Nothing the app collects can be linked by us to a name, an email or an account, because none
  exists.
- `Telemetry.setUser(email, name, id)` is declared at
  `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/Telemetry.kt:6` and implemented at
  `libraries/sodogku/impl/src/commonMain/kotlin/com/sodogku/libraries/sodogku/impl/AppTelemetry.kt:127`,
  and **nothing calls it**. Re-run on 2026-09-10: `grep -rn "setUser(" --include=*.kt .` returns
  exactly three hits, the declaration, the override, and the `Sentry.setUser` call inside that
  override (`AppTelemetry.kt:132`). No email or name reaches Sentry.
- `sendDefaultPii = false` (`AppTelemetry.kt:415`, applied at `:351`), so the Sentry SDK does not
  attach IP or user agent on its own.

**The one qualification, added this pass: on iOS a third party's account is now in the picture.**
Game Center is signed in at the OS level, and the app submits scores under it (§2.11). We never
read the player's Game Center id, alias or any other attribute, and nothing about it is stored or
sent anywhere of ours. But "there is no account anywhere near this app" is no longer the sentence
to write in a privacy policy, and `pages/privacy.html` already says the longer true thing.

What *is* collected by us rides on one pseudonymous identifier, `AppData.installId`, described
below.

**The kids-versus-general-audience question (SPEC 7.1) is still open**, and it changes the answers
more than anything else in this document. §6 gives the answer set for each branch rather than
picking one. Everything in §4 and §5 is written for the **general audience** branch, which is what
the code currently implements (`AdMobAdNetwork.kt:148`,
`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`).

---

## 2. Data inventory, traced

### 2.1 Install ID, the one identifier everything hangs off

| | |
|---|---|
| **What** | A random UUID v4, app-generated. Not a hardware id, not the advertising id, not derived from anything about the device. |
| **Minted** | `libraries/sodogku/impl/src/commonMain/kotlin/com/sodogku/libraries/sodogku/impl/CachedInstallIdProvider.kt:57`, `Uuid.random()` on first read, then persisted. |
| **Stored** | `AppData.installId`, `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/AppCache.kt:159`, written to `<filesDir>/app_data` by `CacheFactory.persistent` (`AppCache.kt:231`). |
| **Leaves the device, 1** | `X-Install-Id` request header on every call to our own server. Assembled at `libraries/networking/impl/.../DefaultClientHeadersProvider.kt:46`, attached at `libraries/networking/impl/.../NetworkClientImpl.kt:159`, header name at `libraries/networking/src/.../ClientHeaders.kt:48`. The only endpoint the shipping client calls is `GET /v1/app-config` (`libraries/config/impl/.../data/RemoteConfigRemoteDataSource.kt:55`). Re-checked by grepping every `client.get` / `client.post` in `libraries`, `features` and `apps`: the only other one is the OTLP exporter, and the rest are in `apps/admin`, which is a separate web app and ships in no binary a player installs. |
| **Leaves the device, 2** | As the `install_id` attribute on **every** OTLP log record sent to Grafana Cloud. `libraries/telemetry/impl/.../GrafanaLogTree.kt:116`, key at `:169`. |
| **Leaves the device, 3** | As a Sentry scope **tag** named `install_id`. `AppTelemetry.kt:162-164`, wired on session start by `libraries/sodogku/impl/.../SessionTelemetryBinder.kt:54-55`. Because it is on the scope, a native crash symbolicated on the next launch still carries it. |
| **What our server does with it** | Read into `ClientContext` (`apps/server/src/main/kotlin/com/sodogku/server/http/ClientContext.kt:77`); used as the bucketing key for config rollouts and allow/deny targeting (`apps/server/.../data/AppConfigTargetingEngine.kt:79-98`); lifted into logging MDC (`plugins/Observability.kt:48`), onto OTel spans (`plugins/Tracing.kt:90`) and onto the server's Sentry scope (`plugins/Sentry.kt:57,65`). It is not written to Postgres. |
| **Lifetime** | Dies with the install. The KDoc at `AppCache.kt:152-158` says so and it is now true on both platforms, because Android Auto Backup is off (§7.1). |
| **Shown to the user** | Never. Re-checked on 2026-09-10 including the new QA tools screen: `grep -rn "installId" --include=*.kt features apps/compose/src` returns nothing. This matters for §7.3. |

### 2.2 Advertising identifier (Android AD_ID / iOS IDFA)

Collected by the Google Mobile Ads SDK, not by our code. We never read it directly.

- Android SDK: `com.google.android.gms:play-services-ads:24.9.0` (`gradle/libs.versions.toml:48,190`),
  used in `libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt`. The SDK's own manifest
  contributes `com.google.android.gms.permission.AD_ID` through manifest merge; it is not in
  `apps/compose/src/androidMain/AndroidManifest.xml`, which declares no permissions of its own.
- **iOS SDK, and this is the change:** the Google Mobile Ads Swift package is now a resolved
  dependency of the Xcode project (`apps/ios/iosApp.xcodeproj/project.pbxproj:410-417`, product
  `GoogleMobileAds` at `:428-429`). The `#if canImport(GoogleMobileAds)` guards in
  `apps/ios/iosApp/Platform/AdNetwork.swift` therefore compile in. iOS serves real ads and the SDK
  reads the IDFA after ATT.
- Child-directed flag: `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` at `AdMobAdNetwork.kt:145-151`.
  `tagForUnderAgeOfConsent` is deliberately left unset, with the reason at `AdMobAdNetwork.kt:56-59`.
- EEA/UK consent runs before the first request: `AdMobAdNetwork.consentThenInitialise()`
  (`:123-158`), gated on `consent.canRequestAds()` at `:139`, form shown at `:176-185`. Order is
  enforced inside `prepare()` (`:86`) rather than at call sites, with the reasoning in the class
  KDoc at `:42-59`.
- iOS: App Tracking Transparency prompt at `AdNetwork.swift:227-231`, fired from the ad prepare
  path at `:79`, with `NSUserTrackingUsageDescription` at `apps/ios/iosApp/Info.plist:37`.
  `GADApplicationIdentifier` at `Info.plist:29`.
- **Today the app requests Google's published *test* ad units.** `AdUnits.useTestUnits = true`
  (`libraries/ads/src/commonMain/.../AdUnits.kt:39`), and both the manifest
  (`AndroidManifest.xml:74-76`, `com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist:29`
  carry Google's sample app ids. **This does not change the disclosure.** The SDK is still
  initialised and still reads the advertising identifier, so from the first public release the
  ad-id rows below are required on both platforms.

### 2.3 Product analytics to Grafana Cloud

- Transport: `libraries/telemetry/impl/.../GrafanaLogTree.kt` emits OTLP log records
  (`:110-141`); `OtlpJsonLogRecordExporter.kt:42` POSTs them to `{GRAFANA_OTLP_BASE_URL}/v1/logs`
  with a build-time basic-auth write token (`GrafanaAppEvents.kt:139-149`, header applied at
  `:167`). Deliberately direct to Grafana rather than through our own backend, so reliability
  events survive a backend outage.
- Every record carries `session_id`, `install_id`, `is_offline` (`GrafanaLogTree.kt:115-117`) plus
  resource attributes `service.name=sodogku-client`, `service.version`, `deployment.environment`,
  `platform`, `build_number`, `commit_sha`, `release_channel` (`GrafanaLogTree.kt:57-69`).
- Payload contents: the event registry in `docs/practices/app-events.md`. Level ids, grid sizes,
  difficulty tiers, durations, scores, paw ratings, strike counts, booster use, streak days,
  tutorial step names, ad outcomes and latencies, purchase outcomes, launch-gate hits, and on iOS
  the two leaderboard values. Two attributes are new since the last pass, `strikes_used` and
  `paws` on `game.level_completed` (`features/game/impl/.../GameViewModel.kt:1549-1563`); both are
  measurements of a finished board and neither introduces a data type.
- **A second mode forwards plain Warn-and-above log lines**, not just events:
  `GrafanaLogTree.kt:94-103`. Those carry the log body, the logger `tag`, and
  `exception_type` / `exception_message` (`:118-124`). The body is whatever our own code passed to
  `KLog`.
- Buffered to disk before export at `<filesDir>/telemetry` (`GrafanaAppEvents.kt:66,79`), retained
  to the library defaults of 100 batches / 30 days (`docs/practices/app-events.md`).
- Kill switches exist but are **operator-side, not user-side**:
  `telemetry.appEventsEnabled`, `telemetry.appEventsSampleRate`, `telemetry.klogForwardingEnabled`
  in remote config (defaults at `libraries/config/impl/.../model/FallbackConfigMap.kt:150-152`).
  There is still no in-app analytics opt-out. See §7.3.
- **Grafana Cloud is a processor on our own account, not a data recipient.** Under Play's
  definition, transfer to a service provider processing on our behalf is not "sharing". Same for
  Sentry. AdMob is different: see §4.

### 2.4 Crash and diagnostics (Sentry), now actually switched on

- `AppTelemetry.kt` initialises the Sentry KMP SDK at `:56-124`, and the init is real rather than
  skipped: `isEnabled` is `dsn.isNotBlank()` (`:343`) and the DSN now resolves. See change 1 above
  for where the value comes from. A fresh clone with no `local.properties` entry still gets a blank
  DSN and reports nothing, so "is Sentry on" is a property of the build, not of the source.
- `attachStacktrace = true` (`:416`), `sendDefaultPii = false` (`:415`), traces sampled at 0.15 in
  release (`:409`), `enableAutoSessionTracking = true` (`:425`), which means Sentry session records
  ship alongside events.
- Scope carries `platform`, `build_type`, `release_channel` extras and `commit_sha`,
  `commit_branch` tags (`:109-116`); `route` on every navigation (`:149-150`); `session_id`
  (`:159`); `install_id` (`:164`).
- Breadcrumbs are KLog entries at Info and above in release (`:421`, `logPolicy`).
- Error events are never sampled away: `options.sampleRate` is deliberately untouched (`:354-357`).
- iOS gets the SDK through the `sentry-cocoa` Swift package (`project.pbxproj:402-409`).

### 2.5 In-app feedback and bug reports, the row people forget

- `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/FeedbackRepository.kt:24-31`
  declares the call and `:40-56` forwards it to `Telemetry.captureUserFeedback`, implemented at
  `AppTelemetry.kt:175-291`. There is no Sodogku feedback backend; a report is a Sentry event, and
  if Sentry is disabled it is dropped with a log line (`:193-198`).
- What is sent, all of it on one carrier event minted by `captureMessage` (`:220`):
  - the player's **verbatim free text**, three times over: in the `UserFeedback.comments` body
    (`:261-273`), as a `feedback_message` extra (`:243`), and as a `feedback.txt` attachment
    (`:244-246`). The duplication is deliberate and the comment at `:231-242` says why: the legacy
    User Feedback API renders its comments somewhere that depends on org settings, and a report
    arrived in the field with its evidence visible and its message nowhere.
  - build version, commit sha and branch (`:266`),
  - **the in-memory session log buffer as a `session-log.txt` attachment** (`:218` snapshot, `:248`
    attach), when `includeLogs` is true, which is the default (`FeedbackRepository.kt:30`) and
    which neither player-facing caller overrides. In release the buffer holds Debug and above
    (`AppTelemetry.kt:423`).
  - up to three **image attachments** named `screenshot-N.jpg` (`:253-258`, cap at `:329`).
- Who calls it, which is what decides whether a store build can produce any of the above:
  - `features/settings/impl/.../feedback/FeedbackViewModel.kt:50`, message and kind only, so logs
    ride along and screenshots do not.
  - `features/home/impl/.../bugreport/BugReportViewModel.kt:50-55`, same plus a log id and error
    code.
  - `apps/compose/src/commonMain/kotlin/com/sodogku/devfeedback/DevFeedbackViewModel.kt:59-64`, the
    only caller that passes a screenshot, and the only one that lets the sender turn the logs off.
    Its host is gated on `BuildInfo.isTesterBuild` (`DevFeedbackHost.kt:55`), which is debug or
    TestFlight (`libraries/core/.../BuildInfo.kt:35`). **Not present in a Play or App Store
    build.**
- The screenshot is a capture of the app's own frame, JPEG-encoded in
  `devfeedback/ScreenshotEncoder.kt`. No camera, no photo library, no picker. `PhotoSaver` and
  `rememberCameraPermissionLauncher` exist in `:libraries:ui` as template scaffolding with no call
  sites anywhere.
- What is **not** sent: an email address. `captureUserFeedback` accepts one (`:180`) and sets it on
  the report if present (`:274`), and **no caller passes it**. No email field is rendered by
  `features/settings/impl/.../feedback/FeedbackScreen.kt` or by the bug reporter; grep for `email`
  across both finds nothing.
- Nevertheless the free-text box is user-typed. A player may put their name or email in it. Both
  stores treat that as user-generated content, and it is why the "User content" rows below are
  present even though the app asks for nothing identifying.

### 2.6 Purchases

- `libraries/billing/impl/src/androidMain/.../PlayStoreBilling.kt` and the StoreKit equivalent in
  `apps/ios/iosApp/Platform/StoreBilling.swift`. The app never sees a payment instrument; the
  purchase token stays inside the store flow and is used only for acknowledgement
  (`PlayStoreBilling.kt:232-239`). Nothing of ours transmits it.
- The only persisted trace is a boolean, `AppData.isProEntitled` (`AppCache.kt:185`), device-local,
  written only by `RealEntitlements`.
- Telemetry records the **outcome only**: `iap.purchase_result` carries `outcome`, `error_kind` and
  `trigger`, and `iap.restore_result` carries `outcome` (`docs/practices/app-events.md`,
  Monetization). No price, no order id, no token. `purchase.failed` carries a `product_id`, and
  there is exactly one product.

### 2.7 Device-local, and it genuinely does not leave

- Room, at database version 9 (`libraries/storage/impl/.../db/AppDatabase.kt:22-30`):
  `level_progress` (`libraries/progress/src/.../db/LevelProgressDao.kt:26`), `daily_result`
  (`db/DailyResultDao.kt:30`), **`play_day`** (`db/PlayDayDao.kt:35`, new since the last pass, one
  row per local date the player finished any board and nothing else in the row), `achievement_fact`
  and `achievement_unlock` (`libraries/achievements/src/.../db/AchievementDao.kt:31,65`).
- `AppData`: settings, booster holdings, the in-progress board snapshot, legal acceptance versions
  (`AppCache.kt`).
- There is no sync, no account of ours and no server-side copy. Settings says so on screen,
  "Progress lives on this device" (`libraries/resources/.../values/strings.xml:351-352`), and with
  Auto Backup off (§7.1) that copy is true on both platforms.

### 2.8 Locale and country, and what is *not* location

- `X-Country-Code` (`ClientHeaders.kt:47`) and the standard `Accept-Language` header
  (`ClientHeaders.kt:25`, attached in `NetworkClientImpl.kt:154-158`) are sent to our config
  endpoint. Both come from the OS locale the user set: `Locale.getDefault().country` on Android
  (`libraries/networking/impl/src/androidMain/.../LocaleSource.android.kt`) and
  `NSLocale.currentLocale.countryCode` on iOS (`LocaleSource.ios.kt`).
- Not GPS, not the SIM, not IP geolocation. No location permission is declared on either platform.
  **Neither store's "Location" category applies.**

### 2.9 IP address

- Our server reads the client IP as an in-memory rate-limit bucket key only
  (`apps/server/src/main/kotlin/com/sodogku/server/plugins/RateLimits.kt:68-72`). It is not stored
  and not used to derive location.
- AdMob, Sentry, Grafana Cloud and, on iOS, Apple's Game Center necessarily observe the IP as the
  origin of the requests they receive. That is disclosed in the privacy policy rather than as a
  Data safety data type: Play's form has no IP-address type, and only asks about location if IP is
  used to derive it. We do not.

### 2.10 Permissions, and why the list is so short

- **The app declares no Android permissions.** `apps/compose/src/androidMain/AndroidManifest.xml`
  contains no `uses-permission` element, and it is the only `AndroidManifest.xml` in the tree
  outside build output. Everything on the merged list arrives from a bundled library, which today
  means the ads SDK. The 2026-09-08 claim that `android.permission.CAMERA` was declared was simply
  wrong, and the §7.2 finding built on it is withdrawn.
- iOS declares two usage strings and no more: `NSUserTrackingUsageDescription` (`Info.plist:37`)
  for ATT, and `NSUserNotificationsUsageDescription` (`Info.plist:19`), which backs the local
  notification authorisation request in `apps/ios/iosApp/Platform/PermissionManager.swift:70-79`.
  There is **no** remote-notification registration anywhere, no device token, and no camera or
  photo-library usage string. Nothing to declare in either form on any of it, since no photo,
  video, audio or push identifier is collected.

### 2.11 Game Center, iOS only, new since the last derivation

- On iOS the app authenticates against Game Center as soon as `RealLeaderboards` is constructed
  (`libraries/leaderboards/impl/.../RealLeaderboards.kt:89-94`), using
  `GKLocalPlayer.local.authenticateHandler` (`GameCenterServices.kt:99-107`).
- It submits two values and only when the platform reports an authenticated player:
  **lifetime score** and **longest streak** (`Leaderboard.kt`), through
  `GKLeaderboard.submitScore` (`GameCenterServices.kt:109-133`). Those go to Apple, under the
  player's Game Center identity, and we receive nothing back and store nothing.
- We never read the Game Center player id, alias or display name. The only trace on our side is the
  event `leaderboard.submitted` with `board` and `value` (`RealLeaderboards.kt:156-160`), which
  carries a score and a board name and no identity.
- On Android there is no path at all: `NoGameServices` reports `Unavailable` from construction, so
  nothing is offered and nothing is sent (`libraries/leaderboards/impl/src/androidMain/.../NoGameServices.kt`).
- What this does to the Apple label is the one open question this pass produced. See §7.6.

---

## 3. What is *not* collected, stated for completeness

Name, email, phone, address, physical address, contacts, calendar, photos from the library, videos,
audio, files, SMS, call logs, health, fitness, location of any kind, browsing history, search
history, installed-app inventory, payment instruments, credit info, push tokens. None of these has
a read path in the tree, and none has a permission or usage string declared.

---

## 4. Google Play, Data safety form

Answer **Yes** to "Does your app collect or share any of the required user data types?"
Answer **Yes** to "Is all of the user data collected by your app encrypted in transit?" (every
egress is HTTPS: the OTLP exporter posts to the Grafana Cloud gateway, Sentry's SDK to its DSN
host, and `NetworkClient` to the app server over TLS. The one caveat is §7.5.)

Answer to "Do you provide a way for users to request that their data be deleted?", see §7.3.
This one is still not answerable from the code as it stands.

| Data type | Collected | Shared | Processed ephemerally | Required or optional | Purposes | Why, and the file that makes it true |
|---|---|---|---|---|---|---|
| **Device or other IDs**, install id | Yes | No | No | Required | App functionality, Analytics | Random UUID minted at `CachedInstallIdProvider.kt:57`, sent as `X-Install-Id` (`NetworkClientImpl.kt:159`), as the `install_id` OTLP attribute (`GrafanaLogTree.kt:116`) and as a Sentry tag (`AppTelemetry.kt:164`). Required because there is no in-app switch that turns it off. |
| **Device or other IDs**, advertising id | Yes | **Yes** | No | Required | Advertising or marketing, Analytics | Collected by `play-services-ads` (`libs.versions.toml:48,190`), not by our code; `AdMobAdNetwork.kt`. Shared because AdMob is a third party, not a processor acting on our behalf. |
| **App activity → App interactions** | Yes | No | No | Required | Analytics, App functionality | The event registry in `docs/practices/app-events.md`, emitted through `GrafanaLogTree.kt:110`. Level starts and completions (now including `strikes_used` and `paws`), boosters, streak days, tutorial steps, ad and paywall funnels. |
| **App info and performance → Crash logs** | Yes | No | No | Required | Analytics (Crash reporting) | `AppTelemetry.kt:56-124`, `attachStacktrace = true` at `:416`. Live in any build with a DSN, which now includes the owner's. |
| **App info and performance → Diagnostics** | Yes | No | No | Required | Analytics | Sentry performance traces at 0.15 (`AppTelemetry.kt:409`), the jank monitor and startup reporter in `:libraries:telemetry:impl`, and the Warn+ log forwarding at `GrafanaLogTree.kt:94-103`. |
| **App info and performance → Other app performance data** | Yes | No | No | Required | Analytics | `app.launched` carries `previous_exit` (crash / anr / oom), from `AndroidPreviousExitProvider` and `IosPreviousExitProvider`. |
| **Messages → Other in-app messages** | Yes | No | No | **Optional** | App functionality, Developer communications | Free-text feedback and bug reports, `FeedbackRepository.kt:40-56` → `AppTelemetry.kt:220`. Optional because it only exists if the player types and submits it. The `session-log.txt` attachment (`AppTelemetry.kt:248`) rides with it, as do the two extra copies of the message (`:243`, `:244-246`). |
| **Financial info → Purchase history** | Yes | No | No | Optional | App functionality, Analytics | The conservative answer. We never see a payment method, and Play's own purchase records are out of scope, but `iap.purchase_result` records to our analytics that a purchase succeeded or failed against an `install_id` (`app-events.md`, Monetization). Declaring it costs nothing; not declaring it is a judgement call you would have to defend. |

**Rows that are deliberately absent:** Location (§2.8), Personal info (§1, §3), Photos and videos
(§2.5, §2.10: the only image the app can send is a capture of its own screen, and only from a
tester build), Contacts, Files and docs, Health and fitness, Web browsing.

### "Linked to the user", read this before you fill the form

Play's definition of collected-and-linked is broad: data is linked if it is collected with, or
associated with, a persistent identifier. Everything in the table above rides with `install_id`,
which is persistent for the life of the install. So none of it qualifies for the
"processed ephemerally" or "anonymous" treatments, and the Data safety form's per-type follow-ups
should be answered on the basis that the data is tied to a device-scoped identifier.

**Confidence: high on the mechanism, medium on Google's current wording.** Verify against Play
Console Help, "Provide information for Google Play's Data safety section", the "Data types" and
"Collection and sharing" sections, at the time you file. The wording has moved before.

### Target audience and content

- **Target audience questionnaire:** 13+ on the general-audience branch, per SPEC 7.1's
  recommendation. Do not enrol in Designed for Families.
- **Ads declaration:** Yes, the app contains ads.
- **Content rating (IARC):** no violence, no user-to-user communication, no gambling, no user
  location sharing. Simulated gambling: **no**. The `High Roller` achievement uses a slot-machine
  emoji as its icon (visible in `docs/store/screenshots/android-phone/07-achievements.png`), which
  is decoration, not a mechanic. Worth mentioning in the questionnaire's free text if it asks.
- **Government app, news app, COVID app:** no.

---

## 5. Apple, App Privacy nutrition label

Filed per app version in App Store Connect. Categories below use Apple's own names. Note that the
label covers the TestFlight build as well, which is the one surface where the tester feedback panel
and its screenshot capture exist (§2.5).

| Apple category → type | Collected | Linked to the user | Used for tracking | Purposes | Mechanism |
|---|---|---|---|---|---|
| **Identifiers → Device ID** | Yes | **See note** | **Yes**, when ATT is granted | Third-Party Advertising, Analytics, App Functionality | The IDFA, read by the Google Mobile Ads SDK after the ATT prompt at `AdNetwork.swift:227-231`. The SDK is now genuinely linked into the app (`project.pbxproj:410-417`). Also our own install id (`CachedInstallIdProvider.kt:57`), which is device-scoped, not user-scoped. |
| **Usage Data → Product Interaction** | Yes | See note | No | Analytics, App Functionality | The event registry, `docs/practices/app-events.md`, via `GrafanaLogTree.kt:110`. |
| **Usage Data → Advertising Data** | Yes | See note | Yes, when ATT is granted | Third-Party Advertising | Impressions and interactions observed by the Google Mobile Ads SDK. Our own side records only outcome and latency (`ads.result`). |
| **Diagnostics → Crash Data** | Yes | See note | No | App Functionality, Analytics | `AppTelemetry.kt:56-124`, through `sentry-cocoa` (`project.pbxproj:402-409`). |
| **Diagnostics → Performance Data** | Yes | See note | No | Analytics | Sentry traces (`AppTelemetry.kt:409`), `IosJankMonitor`, `StartupReporter`. |
| **Diagnostics → Other Diagnostic Data** | Yes | See note | No | Analytics | Warn+ log forwarding with `tag`, `exception_type`, `exception_message` (`GrafanaLogTree.kt:118-124`). |
| **User Content → Other User Content** | Yes | See note | No | App Functionality, Developer Communications | Free-text feedback plus the session-log attachment (`AppTelemetry.kt:243-248`), and in TestFlight builds a capture of the app's own screen (`:253-258`, gated at `DevFeedbackHost.kt:55`). |
| **Purchases → Purchase History** | Yes | See note | No | App Functionality, Analytics | Same conservative reasoning as the Play row. `iap.purchase_result` in `app-events.md`. |

**Not collected, so leave unticked:** Contact Info (all), Health & Fitness, Financial Info other
than purchase history, Location (both precise and coarse), Sensitive Info, Contacts, Browsing
History, Search History, Photos or Videos (§2.10), Identifiers → User ID.

**Identifiers → User ID** is still not applicable *to data we collect*: there is no account of ours
and no user-scoped id anywhere in our records. Game Center complicates the sentence but not, on my
reading, this row, because the score goes to Apple and no Game Center identifier enters anything we
hold. That reading is the open question in §7.6, and it is the one thing in this section I would
not file without checking.

### The "linked to the user" note

Apple's "Data Linked to You" means linked to the user's identity via account, **device**, or other
details, and "Not Linked to You" requires that the data be de-identified and that you not attempt
to relink it to a user *or a device*. Every record we send carries `install_id`, which is exactly a
device-scoped relinkable identifier, so the strict reading puts all of the above under **Data
Linked to You** even though no account of ours exists.

**Confidence: medium.** This is the single answer most likely to be argued, and the one that is
cheapest to get wrong in the safe direction. Filing everything as Linked is conservative and cannot
be a violation; filing as Not Linked while shipping a persistent install id can be. Recommendation:
**file as Linked to You** and say in the privacy policy that the link is to an install, not a
person. Verify against App Store Connect Help, "App privacy details", "Data Linked to You".

### Tracking, which is the answer that gets builds rejected

Apple defines tracking as linking user or device data collected from your app with third-party data
for targeted advertising or ad measurement, or sharing it with a data broker. Serving personalised
AdMob ads is tracking. So:

- **"Used for Tracking" must be ticked** on Device ID and Advertising Data.
- The ATT prompt must be shown before the IDFA is read. It is: `AdNetwork.swift:227-231`, fired
  from the ad `prepare()` path at `:79` rather than at launch, with the usage string at
  `apps/ios/iosApp/Info.plist:37`.
- Apple additionally requires a **privacy manifest** (`PrivacyInfo.xcprivacy`) declaring collected
  data types, required-reason APIs, and tracking domains for the app and each third-party SDK.
  **There is still no `PrivacyInfo.xcprivacy` anywhere in `apps/ios/`.** See §7.4, which is no
  longer blocked by anything.

---

## 6. The kids branch (SPEC 7.1), and exactly what each answer becomes

The decision is unresolved, and SPEC's own decision list still carries it open. It does not merely
adjust a row; on one branch it deletes the ad business and the analytics pipeline.

### Branch A, general audience, 13+ (recommended by SPEC 7.1, and what the code implements)

Everything in §4 and §5 stands as written. Concretely, the code is already correct for this branch:
`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` at `AdMobAdNetwork.kt:148`, `tagForUnderAgeOfConsent` left
unset (`:56-59`), UMP before the first request, ATT on iOS. Do not enrol in Designed for Families;
do not select the Kids Category.

The residual risk is presentation, not code: a heavily kid-appealing icon plus a 13+ declaration
can still draw a Play review flag. The art should read "cute", not "preschool".

### Branch B, child-directed (Play Families) and/or Apple Kids Category

**On Google Play:**

- No advertising id. `com.google.android.gms.permission.AD_ID` must be *removed* by an explicit
  `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"/>`
  in `apps/compose/src/androidMain/AndroidManifest.xml`, because it arrives by manifest merge from
  `play-services-ads` and is not written there today.
- `setTagForChildDirectedTreatment` flips to `TRUE` at `AdMobAdNetwork.kt:148`.
- Only Families-certified ad SDKs, no personalised ads. Revenue per impression falls sharply.
- The Data safety **advertising id row disappears entirely**. The install-id row survives, but
  Families policy restricts using a persistent identifier for advertising, so its purpose list
  loses "Advertising or marketing".
- COPPA and GDPR-K attach: verifiable parental consent obligations, and the UMP flow is no longer
  sufficient on its own.

**On Apple, Kids Category:**

- Third-party analytics and third-party advertising are **banned outright**. That removes AdMob,
  Sentry and the Grafana Cloud pipeline in one stroke, because all three are third parties
  receiving data.
- The nutrition label collapses to **Data Not Collected**, with the possible exception of the
  Purchases row.
- `:libraries:telemetry`, the Sentry tree in `AppTelemetry.kt` and `:libraries:ads` would all have
  to be compiled out or no-op'd on iOS, and the crash pipeline replaced with something first-party.
- Whether Game Center survives the Kids Category, and what a Kids app may do with a leaderboard,
  I could not settle from Apple's published wording. `:libraries:leaderboards` would need an answer
  before this branch could ship. **Not determined.**
- The entire monetization model in SPEC §5 would need rewriting: no rewarded ads means no bone
  refill, no continue, no skip, and no streak freeze.

**This is why SPEC 7.1 says resolve it before ads are wired.** Branch B is not a settings change;
it is a different app.

---

## 7. Not determined, and what each one needs

### 7.1 Android Auto Backup, resolved: off

`android:allowBackup="false"` at `apps/compose/src/androidMain/AndroidManifest.xml:15`, with the
reasoning in the comment above it at `:4-12`. Nothing leaves the device through Auto Backup, so the
Settings copy (`strings.xml:351-352`) and the `AppCache` KDoc (`AppCache.kt:152-158`) are true on
both platforms, the install id cannot outlive the install or reach a second device, and the
question of whether Auto Backup is itself a declarable transfer never has to be answered.

Kept here rather than deleted because it is the reasoning behind an answer two forms depend on, and
because a future release that flips the flag back re-opens every part of it.

### 7.2 Withdrawn: the camera permission that was never there

The 2026-09-08 pass recorded a `android.permission.CAMERA` line to remove from the manifest and a
permission entry it would put on the Play listing. Neither exists. The manifest declares no
permissions at all (§2.10). The scaffolding that suggested otherwise, `rememberCameraPermissionLauncher`
in `:libraries:ui`, is Kotlin with no call sites and no manifest entry of its own, so it changes
nothing about the listing. Nothing to do.

### 7.3 There is no deletion path, and no way for a player to name themselves

Play's Data safety form asks whether users can request deletion of their data, and both GDPR and
CCPA assume an erasure route. Still open, and SPEC's decision list still carries it.

- Device-local data goes when the app is uninstalled, and §7.1 means that is now unqualified.
- Telemetry in Loki ages out at Grafana Cloud's retention; Sentry has its own retention.
- **Neither can be deleted on request in practice**, because the only key is `install_id` and the
  app never shows it to the player. Re-checked this pass against the new QA tools screen as well:
  no screen, no debug menu entry, no share-your-id affordance.
- There is also **no in-app analytics opt-out**. `telemetry.appEventsEnabled` is an operator kill
  switch in remote config, not a user setting.
- `pages/privacy.html` already says this plainly rather than papering over it, which is the right
  interim position but is not an answer to the form's question.

**What this needs:** a product decision. The cheapest fix that makes the form answerable is showing
the install id in Settings (or in the feedback form's payload, where it already effectively is)
plus a support email that accepts deletion requests. A second option is an in-app "share usage
data" toggle wired to the same gate `appEventsEnabled` uses. **Not determined; do not answer this
question on the form until it is.**

### 7.4 iOS privacy manifest is missing, and is no longer blocked

Apple requires a `PrivacyInfo.xcprivacy` for the app, and requires that bundled third-party SDKs
ship signed manifests. There is still no such file anywhere under `apps/ios/`. It has to declare:
collected data types (matching §5), required-reason API usage (`UserDefaults` at minimum, via the
persistent cache), and tracking domains.

The 2026-09-08 note said this was blocked behind adding the Google Mobile Ads package. **That
package is now in the project** (`project.pbxproj:410-417`), along with `sentry-cocoa` (`:402-409`),
so the SDK set for a first iOS release is known and the file can be written. Confirm while writing
it that both packages ship their own signed manifests, which is the SDK-side half of the
requirement and is not something our file can satisfy on their behalf. **What this needs:** the
file authored and added to the iOS target.

### 7.5 Data safety "encrypted in transit" for the Grafana endpoint

`GrafanaCloud.OTLP_BASE_URL` is injected at build time from `local.properties` / CI secrets
(`GrafanaAppEvents.kt:139-149`). Nothing in the code forces `https`. It will be an HTTPS Grafana
Cloud gateway in practice, but the claim on the form is only true if the configured value is.
The Sentry half of that answer is now settled, because a Sentry DSN is an HTTPS URL by
construction. **What this needs:** confirm the Grafana value when the account exists (SPEC §20).

### 7.6 Game Center, which neither form has an obvious row for

New this pass. On iOS the app submits a lifetime score and a longest streak to Apple's Game Center
under the player's Game Center identity (§2.11).

What is clearly true: we collect nothing from it, store nothing, and never read the player's Game
Center id or alias, so no row in §5 gains a value and **Identifiers → User ID** stays unticked on
the reading above.

What I could not settle from Apple's published wording: whether App Privacy expects a developer to
declare data that the app causes to flow into a **first-party Apple service** under the user's
Apple identity, given that the framework is Apple's own and the data never reaches us. The two
plausible answers are "no row, it is Apple's own service and Apple's own privacy policy governs
it", which is what most Game Center apps appear to do, and "a Gameplay Content or Other User
Content row", which is conservative and cannot be a violation.

**What this needs:** one check against App Store Connect Help, "App privacy details", before the
label is filed, plus the Kids-branch question in §6. `pages/privacy.html` already describes the
flow in plain words, which is the part that is definitely required either way. **Not determined.**

---

## 8. Findings for whoever owns the code

Written down rather than fixed, per this chunk's scope. Re-checked on 2026-09-10.

1. ~~**`allowBackup="true"` contradicts a user-facing promise.**~~ Fixed: backup is off, see §7.1.
2. ~~**No reachable Restore purchases control.**~~ Fixed: Settings shows one whether or not the
   player is Pro, with the App Review reasoning in a comment (`SettingsScreen.kt:197-205`).
3. ~~**The camera permission on the listing.**~~ Withdrawn: it was never declared, see §7.2.
4. **The feedback path ships a session log the player is not told about, and now also ships the
   message three times.** `AppTelemetry.kt:248` attaches `session-log.txt` (Debug and above in
   release) to every submission, because `includeLogs` defaults to true
   (`FeedbackRepository.kt:30`) and neither player-facing caller sets it. Only the tester panel
   exposes a switch. That is defensible and probably necessary, but the feedback screen's copy does
   not mention it and the privacy policy has to. Users generally read "send feedback" as "send my
   message".
5. **Dead auth-era rate limits on the server.** `apps/server/.../plugins/RateLimits.kt:23-24` still
   registers `DELETE_ACCOUNT_LIMIT` and `PLAYER_REPORT_LIMIT`, with comments about App Store review
   of a deletion endpoint. Both endpoints were deleted in C0.
6. **Supabase references survive in the shipped Android manifest.**
   `AndroidManifest.xml:43-46` describes `sodogku://auth/confirmed` and `sodogku://login-callback`
   as Supabase OAuth return trips, and `apps/ios/iosApp/Info.plist:72-80` says the same in its
   comment above `CFBundleURLTypes`. Comments only, but a store reviewer reading the manifest sees
   an auth flow the app does not have.
7. **`Telemetry.setUser` is a loaded gun, and it is the one scope writer with no guard.** It is
   implemented and reachable, and would put an email into Sentry the moment anyone called it,
   invalidating §1 and most of §5. Worth a comment on the interface saying so, or deleting it since
   there are no accounts. Separately, `setCurrentRoute`, `setSession`, `setInstallId` and
   `setContext` all begin with `if (!Sentry.isEnabled()) return` (`AppTelemetry.kt:145,158,163,168`)
   and `setUser` (`:127-138`) does not, so it alone would call into the SDK before or without init.
8. **Two KDocs still describe the account era.** `AppCache.kt:154-156` says the install id is
   "sent as X-Install-Id on authenticated requests so the server can associate anonymous accounts
   from the same install", and the `UserScopedClearer` KDoc below `AppData` describes resetting
   account-scoped fields on sign-out and account switch. Both describe machinery removed in C0.
   Harmless at runtime, misleading to anyone deriving a privacy answer from them, which is exactly
   what this file does.
