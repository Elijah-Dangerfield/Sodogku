# Data safety and privacy nutrition labels

Answers for Google Play's **Data safety** form and Apple's **App Privacy** (nutrition label)
questionnaire, derived from what the code actually does. Every row cites the file and the
mechanism that makes it true. Where the honest answer is "not determined", it says so and says
where to look, because a guess here is a policy violation rather than a typo.

Derived on 2026-09-08 against the tree at that date. **Re-derive it whenever a network call, an
SDK, or a telemetry attribute changes.** The three things most likely to invalidate a row: a new
`logEvent` attribute (`docs/practices/app-events.md`), a new SDK in `gradle/libs.versions.toml`,
and anything that starts calling `Telemetry.setUser`.

---

## 1. The two facts that shape every answer

**There are no accounts, and identity was deleted rather than disabled.** `:libraries:identity`,
the Supabase auth screens and the `UserScopedSyncer` / `UserScopedDataReset` machinery were
removed in C0 (`AGENTS.md`, "No accounts"; `docs/decisions.md`). The client has no sign-in, no
user id, no server-side user record, and `apps/server` has no auth plugin and no user tables. So:

- Nothing the app collects can be linked to a name, an email or an account, because none exists.
- `Telemetry.setUser(email, name, id)` is declared at
  `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/Telemetry.kt:6` and implemented at
  `libraries/sodogku/impl/src/commonMain/kotlin/com/sodogku/libraries/sodogku/impl/AppTelemetry.kt:115`,
  and **nothing calls it**. Grep for `setUser(` returns the interface, the implementation, and
  nothing else. No email or name reaches Sentry.
- `sendDefaultPii = false` (`AppTelemetry.kt:359`), so the Sentry SDK does not attach IP or user
  agent on its own.

What *is* collected rides on one pseudonymous identifier, `AppData.installId`, described below.

**The kids-versus-general-audience question (SPEC 7.1) is still open**, and it changes the answers
more than anything else in this document. §6 gives the answer set for each branch rather than
picking one. Everything in §4 and §5 is written for the **general audience** branch, which is what
the code currently implements (`AdMobAdNetwork.kt:161`, `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`).

---

## 2. Data inventory, traced

### 2.1 Install ID — the one identifier everything hangs off

| | |
|---|---|
| **What** | A random UUID v4, app-generated. Not a hardware id, not the advertising id, not derived from anything about the device. |
| **Minted** | `libraries/sodogku/impl/src/commonMain/kotlin/com/sodogku/libraries/sodogku/impl/CachedInstallIdProvider.kt:57` — `Uuid.random()` on first read, then persisted. |
| **Stored** | `AppData.installId`, `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/AppCache.kt:97`, written to `<filesDir>/app_data` by `CacheFactory.persistent` (`AppCache.kt:168`). |
| **Leaves the device, 1** | `X-Install-Id` request header on every call to our own server. Assembled at `libraries/networking/impl/.../DefaultClientHeadersProvider.kt:46`, attached at `libraries/networking/impl/.../NetworkClientImpl.kt:159`, header name at `libraries/networking/src/.../ClientHeaders.kt:48`. The only endpoint the shipping client calls is `GET /v1/app-config` (`libraries/config/impl/.../RemoteConfigRemoteDataSource.kt:55`). |
| **Leaves the device, 2** | As the `install_id` attribute on **every** OTLP log record sent to Grafana Cloud. `libraries/telemetry/impl/.../GrafanaLogTree.kt:116` and `:169`. |
| **Leaves the device, 3** | As a Sentry scope **tag** named `install_id`. `AppTelemetry.kt:150-153`, wired on session start by `libraries/sodogku/impl/.../SessionTelemetryBinder.kt:55`. Because it is on the scope, a native crash symbolicated on the next launch still carries it. |
| **What our server does with it** | Read into `ClientContext` (`apps/server/src/main/kotlin/com/sodogku/server/http/ClientContext.kt:77`); used as the bucketing key for config rollouts and allow/deny targeting (`apps/server/.../data/AppConfigTargetingEngine.kt:79-98`); lifted into logging MDC (`plugins/Observability.kt:48`), onto OTel spans (`plugins/Tracing.kt:90`) and onto the server's Sentry scope (`plugins/Sentry.kt:57`). It is not written to Postgres. |
| **Lifetime** | The KDoc at `AppCache.kt:92` says it "dies with uninstall". On Android that is **not currently true** — see §7.1. |
| **Shown to the user** | Never. No screen in `features/` renders it; grep for `installId` under `features/` returns nothing. This matters for §7.3. |

### 2.2 Advertising identifier (Android AD_ID / iOS IDFA)

Collected by the Google Mobile Ads SDK, not by our code. We never read it directly.

- SDK: `com.google.android.gms:play-services-ads:24.9.0` (`gradle/libs.versions.toml:48,190`),
  used in `libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt`. The SDK's own manifest
  contributes `com.google.android.gms.permission.AD_ID` through manifest merge; it is not in
  `apps/compose/src/androidMain/AndroidManifest.xml`.
- Child-directed flag: `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` at `AdMobAdNetwork.kt:161-164`.
  `tagForUnderAgeOfConsent` is deliberately left unset, with the reason at `AdMobAdNetwork.kt:60-63`.
- EEA/UK consent runs before the first request: `AdMobAdNetwork.consentThenInitialise()`
  (`:137-172`), gated on `consent.canRequestAds()` at `:153`, form shown at `:190-199`. Order is
  enforced inside `prepare()` rather than at call sites (class KDoc, `:44-58`).
- iOS: App Tracking Transparency prompt at
  `apps/ios/iosApp/Platform/AdNetwork.swift:272-275`, with `NSUserTrackingUsageDescription` at
  `apps/ios/iosApp/Info.plist:37`. `GADApplicationIdentifier` at `Info.plist:29`.
- **Today the app requests Google's published *test* ad units.** `AdUnits.useTestUnits = true`
  (`libraries/ads/src/commonMain/.../AdUnits.kt:39`), and both the manifest
  (`AndroidManifest.xml`, `com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist` carry
  Google's sample app ids. **This does not change the disclosure.** The SDK is still initialised
  and still reads the advertising identifier, so from the first public release that ships ads on,
  the ad-id rows below are required.
- iOS serves no ads at all until the Google Mobile Ads Swift package is added to the Xcode project
  (`AdNetwork.swift` is behind `#if canImport(GoogleMobileAds)`, and SPEC §20 lists the step). If
  the first iOS release genuinely ships without that package, the iOS label's advertising rows are
  not yet true. **Do not file the label on that basis** unless you intend to ship iOS ad-free and
  re-file when ads land.

### 2.3 Product analytics to Grafana Cloud

- Transport: `libraries/telemetry/impl/.../GrafanaLogTree.kt` emits OTLP log records;
  `OtlpJsonLogRecordExporter.kt:42` POSTs them to `{GRAFANA_OTLP_BASE_URL}/v1/logs` with a
  build-time basic-auth write token (`GrafanaAppEvents.kt:139-149`). Deliberately direct to
  Grafana rather than through our own backend, so reliability events survive a backend outage.
- Every record carries `session_id`, `install_id`, `is_offline` (`GrafanaLogTree.kt:115-117`) plus
  resource attributes `service.name=sodogku-client`, `service.version`, `deployment.environment`,
  `platform`, `build_number`, `commit_sha`, `release_channel` (`GrafanaLogTree.kt:57-70`).
- Payload contents: the event registry in `docs/practices/app-events.md`. Level ids, grid sizes,
  difficulty tiers, durations, scores, paw ratings, strike counts, booster use, daily streaks,
  tutorial step names, ad outcomes and latencies, purchase outcomes, launch-gate hits.
- **A second mode forwards plain Warn-and-above log lines**, not just events:
  `GrafanaLogTree.kt:96-124`. Those carry the log body, the logger `tag`, and
  `exception_type` / `exception_message`. The body is whatever our own code passed to `KLog`.
- Buffered to disk before export at `<filesDir>/telemetry` (`GrafanaAppEvents.kt:67,79`), retained
  to the library defaults of 100 batches / 30 days (`docs/practices/app-events.md`).
- Kill switches exist but are **operator-side, not user-side**:
  `telemetry.appEventsEnabled`, `telemetry.appEventsSampleRate`, `telemetry.klogForwardingEnabled`
  in remote config. There is no in-app analytics opt-out. See §7.3.
- **Grafana Cloud is a processor on our own account, not a data recipient.** Under Play's
  definition, transfer to a service provider processing on our behalf is not "sharing". Same for
  Sentry. AdMob is different: see §4.

### 2.4 Crash and diagnostics (Sentry)

- `AppTelemetry.kt` initialises the Sentry KMP SDK. `attachStacktrace = true` (`:360`),
  `sendDefaultPii = false` (`:359`), traces sampled at 0.15 in release (`:353`).
- Scope carries `platform`, `build_type`, `release_channel` extras and `commit_sha`,
  `commit_branch` tags (`:98-104`); `route` on every navigation (`:137-138`); `session_id`
  (`:147`); `install_id` (`:152`).
- Breadcrumbs are KLog entries at Info and above in release (`:354`, `logPolicy`).
- Error events are never sampled away: `options.sampleRate` is deliberately untouched (`:303-306`).

### 2.5 In-app feedback and bug reports — the row people forget

- `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/FeedbackRepository.kt:38-50`
  forwards to `Telemetry.captureUserFeedback`, implemented at `AppTelemetry.kt:163-251`. There is
  no Sodogku feedback backend; a report is a Sentry event.
- What is sent: the player's **verbatim free text** (`AppTelemetry.kt:224-235`), the build version,
  commit sha and branch, and — this is the one worth reading twice — **the in-memory session log
  buffer as a `session-log.txt` attachment** (`AppTelemetry.kt:205` snapshot, `:210` attach). In
  release the buffer holds Debug and above (`AppTelemetry.kt:367`).
- What is **not** sent: `captureUserFeedback` accepts `email` and `screenshots`, and
  `FeedbackRepositoryImpl` passes neither (`FeedbackRepository.kt:44-49`), so both take their
  defaults of `null` and empty. No email field is rendered by
  `features/settings/impl/.../feedback/FeedbackScreen.kt`.
- Nevertheless the free-text box is user-typed. A player may put their name or email in it. Both
  stores treat that as user-generated content, and it is why the "User content" rows below are
  present even though the app asks for nothing identifying.

### 2.6 Purchases

- `libraries/billing/impl/src/androidMain/.../PlayStoreBilling.kt` and the StoreKit equivalent in
  `apps/ios/iosApp/Platform/StoreBilling.swift`. The app never sees a payment instrument; the
  purchase token stays inside the store flow and is used only for acknowledgement
  (`PlayStoreBilling.kt:239`). Nothing of ours transmits it.
- The only persisted trace is a boolean, `AppData.isProEntitled` (`AppCache.kt:123`), device-local,
  written only by `RealEntitlements`.
- Telemetry records the **outcome only**: `iap.purchase_result` carries `outcome` and `error_kind`,
  and `iap.restore_result` carries `outcome` (`docs/practices/app-events.md`, Monetization). No
  price, no order id, no token. `purchase.failed` carries a `product_id`, and there is exactly one
  product.

### 2.7 Device-local, and it genuinely does not leave

- Room: `level_progress` (`libraries/progress/src/.../db/LevelProgressDao.kt:26`), `daily_result`
  (`db/DailyResultDao.kt:30`), `achievement_fact` and `achievement_unlock`
  (`libraries/achievements/src/.../db/AchievementDao.kt:31,65`).
- `AppData`: settings, booster holdings, the in-progress board snapshot, legal acceptance versions
  (`AppCache.kt`).
- There is no sync, no account and no server-side copy. Settings says so on screen ("Progress lives
  on this device"). §7.1 is the caveat on that sentence.

### 2.8 Locale and country, and what is *not* location

- `X-Country-Code` (`ClientHeaders.kt:47`) and the standard `Accept-Language` header
  (`ClientHeaders.kt:26`, attached in `NetworkClientImpl`) are sent to our config endpoint. Both
  come from the OS locale the user set:
  `Locale.getDefault().country` on Android
  (`libraries/networking/impl/src/androidMain/.../LocaleSource.android.kt`) and
  `NSLocale.currentLocale.countryCode` on iOS (`LocaleSource.ios.kt`).
- Not GPS, not the SIM, not IP geolocation. No location permission is declared on either platform.
  **Neither store's "Location" category applies.**

### 2.9 IP address

- Our server reads the client IP as an in-memory rate-limit bucket key only
  (`apps/server/src/main/kotlin/com/sodogku/server/plugins/RateLimits.kt:68-73`). It is not stored
  and not used to derive location.
- AdMob, Sentry and Grafana Cloud necessarily observe the IP as the origin of the requests they
  receive. That is disclosed in the privacy policy rather than as a Data safety data type: Play's
  form has no IP-address type, and only asks about location if IP is used to derive it. We do not.

### 2.10 Camera

- `android.permission.CAMERA` is declared at `apps/compose/src/androidMain/AndroidManifest.xml:4`
  with a matching `uses-feature required="false"`.
- **Nothing in the app uses it.** `CameraPreview` and `rememberCameraPermissionLauncher` exist only
  in `:libraries:ui` as template scaffolding; grep across `features/` and `apps/` finds no call
  site. iOS declares no `NSCameraUsageDescription` at all, which confirms it.
- Nothing to declare in either form, since no photo or video data is collected. It still shows on
  the Play listing's permission list, which is a bad look on a puzzle game and an invitation for a
  reviewer to ask. See §7.2.

---

## 3. What is *not* collected, stated for completeness

Name, email, phone, address, physical address, contacts, calendar, photos, videos, audio, files,
SMS, call logs, health, fitness, location of any kind, browsing history, search history,
installed-app inventory, payment instruments, credit info. None of these has a read path in the
tree, and most have no permission declared.

---

## 4. Google Play — Data safety form

Answer **Yes** to "Does your app collect or share any of the required user data types?"
Answer **Yes** to "Is all of the user data collected by your app encrypted in transit?" (every
egress is HTTPS: the OTLP exporter posts to the Grafana Cloud gateway, Sentry's SDK to its DSN
host, and `NetworkClient` to the app server over TLS.)

Answer to "Do you provide a way for users to request that their data be deleted?" — see §7.3.
This one is not answerable from the code as it stands.

| Data type | Collected | Shared | Processed ephemerally | Required or optional | Purposes | Why, and the file that makes it true |
|---|---|---|---|---|---|---|
| **Device or other IDs** — install id | Yes | No | No | Required | App functionality, Analytics | Random UUID minted at `CachedInstallIdProvider.kt:57`, sent as `X-Install-Id` (`NetworkClientImpl.kt:159`), as the `install_id` OTLP attribute (`GrafanaLogTree.kt:116`) and as a Sentry tag (`AppTelemetry.kt:152`). Required because there is no in-app switch that turns it off. |
| **Device or other IDs** — advertising id | Yes | **Yes** | No | Required | Advertising or marketing, Analytics | Collected by `play-services-ads` / the iOS SDK, not by our code; `AdMobAdNetwork.kt`. Shared because AdMob is a third party, not a processor acting on our behalf. |
| **App activity → App interactions** | Yes | No | No | Required | Analytics, App functionality | The event registry in `docs/practices/app-events.md`, emitted through `GrafanaLogTree.kt:110`. Level starts and completions, boosters, daily streaks, tutorial steps, ad and paywall funnels. |
| **App info and performance → Crash logs** | Yes | No | No | Required | Analytics (Crash reporting) | `AppTelemetry.kt:52-113`, `attachStacktrace = true` at `:360`. |
| **App info and performance → Diagnostics** | Yes | No | No | Required | Analytics | Sentry performance traces at 0.15 (`AppTelemetry.kt:353`), the jank monitor and startup reporter in `:libraries:telemetry:impl`, and the Warn+ log forwarding at `GrafanaLogTree.kt:96-98`. |
| **App info and performance → Other app performance data** | Yes | No | No | Required | Analytics | `app.launched` carries `previous_exit` (crash / anr / oom), from `AndroidPreviousExitProvider` and `IosPreviousExitProvider`. |
| **Messages → Other in-app messages** | Yes | No | No | **Optional** | App functionality, Developer communications | Free-text feedback and bug reports, `FeedbackRepository.kt:38-50` → `AppTelemetry.kt:207`. Optional because it only exists if the player types and submits it. The `session-log.txt` attachment (`AppTelemetry.kt:210`) rides with it. |
| **Financial info → Purchase history** | Yes | No | No | Optional | App functionality, Analytics | The conservative answer. We never see a payment method, and Play's own purchase records are out of scope, but `iap.purchase_result` records to our analytics that a purchase succeeded or failed against an `install_id` (`app-events.md`, Monetization). Declaring it costs nothing; not declaring it is a judgement call you would have to defend. |

**Rows that are deliberately absent:** Location (§2.8), Personal info (§1, §3), Photos and videos
(§2.10), Contacts, Files and docs, Health and fitness, Web browsing.

### "Linked to the user" — read this before you fill the form

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
  location sharing. Simulated gambling: **no** — the `High Roller` achievement uses a slot-machine
  emoji as its icon (visible in `docs/store/screenshots/android-phone/07-achievements.png`), which
  is decoration, not a mechanic. Worth mentioning in the questionnaire's free text if it asks.
- **Government app, news app, COVID app:** no.

---

## 5. Apple — App Privacy nutrition label

Filed per app version in App Store Connect. Categories below use Apple's own names.

| Apple category → type | Collected | Linked to the user | Used for tracking | Purposes | Mechanism |
|---|---|---|---|---|---|
| **Identifiers → Device ID** | Yes | **See note** | **Yes**, when ATT is granted | Third-Party Advertising, Analytics, App Functionality | The IDFA, read by the Google Mobile Ads SDK after the ATT prompt at `AdNetwork.swift:272-275`. Also our own install id (`CachedInstallIdProvider.kt:57`), which is device-scoped, not user-scoped. |
| **Usage Data → Product Interaction** | Yes | See note | No | Analytics, App Functionality | The event registry, `docs/practices/app-events.md`, via `GrafanaLogTree.kt:110`. |
| **Usage Data → Advertising Data** | Yes | See note | Yes, when ATT is granted | Third-Party Advertising | Impressions and interactions observed by the Google Mobile Ads SDK. Our own side records only outcome and latency (`ads.result`). |
| **Diagnostics → Crash Data** | Yes | See note | No | App Functionality, Analytics | `AppTelemetry.kt:52-113`. |
| **Diagnostics → Performance Data** | Yes | See note | No | Analytics | Sentry traces (`AppTelemetry.kt:353`), `IosJankMonitor`, `StartupReporter`. |
| **Diagnostics → Other Diagnostic Data** | Yes | See note | No | Analytics | Warn+ log forwarding with `tag`, `exception_type`, `exception_message` (`GrafanaLogTree.kt:118-124`). |
| **User Content → Other User Content** | Yes | See note | No | App Functionality, Developer Communications | Free-text feedback plus the session-log attachment, `AppTelemetry.kt:207-221`. |
| **Purchases → Purchase History** | Yes | See note | No | App Functionality, Analytics | Same conservative reasoning as the Play row. `iap.purchase_result` in `app-events.md`. |

**Not collected, so leave unticked:** Contact Info (all), Health & Fitness, Financial Info other
than purchase history, Location (both precise and coarse), Sensitive Info, Contacts, User Content
other than the above, Browsing History, Search History, Identifiers → User ID.

Note that **Identifiers → User ID** is genuinely not applicable: there is no account and no
user-scoped id anywhere in the app.

### The "linked to the user" note

Apple's "Data Linked to You" means linked to the user's identity via account, **device**, or other
details, and "Not Linked to You" requires that the data be de-identified and that you not attempt
to relink it to a user *or a device*. Every record we send carries `install_id`, which is exactly a
device-scoped relinkable identifier, so the strict reading puts all of the above under **Data
Linked to You** even though no account exists.

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
- The ATT prompt must be shown before the IDFA is read. It is: `AdNetwork.swift:272-275`, fired
  from the ad `prepare()` path rather than at launch, with the usage string at
  `apps/ios/iosApp/Info.plist:37`.
- Apple additionally requires a **privacy manifest** (`PrivacyInfo.xcprivacy`) declaring collected
  data types, required-reason APIs, and tracking domains for the app and each third-party SDK.
  **There is no `PrivacyInfo.xcprivacy` anywhere in `apps/ios/`.** See §7.4.

---

## 6. The kids branch (SPEC 7.1), and exactly what each answer becomes

The decision is unresolved. It does not merely adjust a row; on one branch it deletes the ad
business and the analytics pipeline.

### Branch A — general audience, 13+ (recommended by SPEC 7.1, and what the code implements)

Everything in §4 and §5 stands as written. Concretely, the code is already correct for this branch:
`TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` at `AdMobAdNetwork.kt:161`, `tagForUnderAgeOfConsent` left
unset at `AdMobAdNetwork.kt:60-63`, UMP before the first request, ATT on iOS. Do not enrol in
Designed for Families; do not select the Kids Category.

The residual risk is presentation, not code: a heavily kid-appealing icon plus a 13+ declaration
can still draw a Play review flag. The art should read "cute", not "preschool".

### Branch B — child-directed (Play Families) and/or Apple Kids Category

**On Google Play:**

- No advertising id. `com.google.android.gms.permission.AD_ID` must be *removed* by an explicit
  `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"/>`
  in `apps/compose/src/androidMain/AndroidManifest.xml`, because it arrives by manifest merge from
  `play-services-ads` and is not written there today.
- `setTagForChildDirectedTreatment` flips to `TRUE` at `AdMobAdNetwork.kt:161`.
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
- The entire monetization model in SPEC §5 would need rewriting: no rewarded ads means no bone
  refill, no continue, no skip, and no streak freeze.

**This is why SPEC 7.1 says resolve it before ads are wired.** Branch B is not a settings change;
it is a different app.

---

## 7. Not determined, and what each one needs

### 7.1 Android Auto Backup contradicts what Settings tells the player

`apps/compose/src/androidMain/AndroidManifest.xml:10` sets `android:allowBackup="true"` and
declares no `android:dataExtractionRules` and no `android:fullBackupContent`.
`AndroidFileManager.kt:21` writes the persistent cache to `context.filesDir`, and the Room database
lives in the default `databases/` directory. Both are inside Auto Backup's default scope.

So on Android, `app_data` (including `installId`), `level_progress`, `daily_result` and the
achievement tables are eligible to be uploaded to the user's Google Drive and restored onto a new
device or a reinstall.

Two consequences:

1. **The Settings copy is falsifiable.** The app tells the player "There are no accounts, so
   levels, streaks and badges do not survive a reinstall or move to a new phone." On Android with
   backup enabled, they may. Same for the KDoc at `AppCache.kt:92`, "dies with uninstall".
2. **The install id can outlive the install**, which changes how long the identifier persists and
   therefore how long the analytics identity persists.

**What this needs:** a decision, then either backup rules that exclude `app_data` and the telemetry
buffer (or all of it), or a change to the Settings copy and the KDoc. Whether Auto Backup itself is
a declarable transfer in the Data safety form is a separate question I could not settle from
Google's published wording; the platform, not the app, performs it and the user controls it. Verify
in Play Console Help before answering. **Not determined.**

### 7.2 The camera permission is a template leftover on the listing

Declared at `AndroidManifest.xml:4`, used by nothing (§2.10). It contributes no Data safety row,
but Play publishes the permission list on the store page. **What this needs:** the manifest line
removed, which is a Kotlin-adjacent change outside this chunk's scope and is written down here
rather than made.

### 7.3 There is no deletion path, and no way for a player to name themselves

Play's Data safety form asks whether users can request deletion of their data, and both GDPR and
CCPA assume an erasure route.

- Device-local data goes when the app is uninstalled, modulo §7.1.
- Telemetry in Loki ages out at Grafana Cloud's retention; Sentry has its own retention.
- **Neither can be deleted on request in practice**, because the only key is `install_id` and the
  app never shows it to the player. There is no screen, no debug menu entry and no share-your-id
  affordance; grep for `installId` under `features/` returns nothing.
- There is also **no in-app analytics opt-out**. `telemetry.appEventsEnabled` is an operator kill
  switch in remote config, not a user setting.

**What this needs:** a product decision. The cheapest fix that makes the form answerable is showing
the install id in Settings (or in the feedback form's payload, where it already effectively is) plus
a support email that accepts deletion requests. A second option is an in-app "share usage data"
toggle wired to the same gate `appEventsEnabled` uses. **Not determined; do not answer this
question on the form until it is.**

### 7.4 iOS privacy manifest is missing

Apple requires a `PrivacyInfo.xcprivacy` for the app, and requires that bundled third-party SDKs
ship signed manifests. There is no such file anywhere under `apps/ios/`. It has to declare:
collected data types (matching §5), required-reason API usage (`UserDefaults` at minimum, via the
persistent cache), and tracking domains. **What this needs:** the file authored and added to the
iOS target, once the SDK set is final. Blocked behind adding the Google Mobile Ads package, which is
itself on SPEC §20.

### 7.5 Data safety "encrypted in transit" for the Grafana endpoint

`GrafanaCloud.OTLP_BASE_URL` is injected at build time from `local.properties` / CI secrets
(`GrafanaAppEvents.kt:128-140`). Nothing in the code forces `https`. It will be an HTTPS Grafana
Cloud gateway in practice, but the claim on the form is only true if the configured value is.
**What this needs:** confirm the value when the Grafana Cloud account exists (SPEC §20).

---

## 8. Findings for whoever owns the code

Written down rather than fixed, per this chunk's scope.

1. **`allowBackup="true"` contradicts a user-facing promise.** §7.1. The highest-value item here.
2. **The feedback path ships a session log the player is not told about.** `AppTelemetry.kt:210`
   attaches `session-log.txt` (Debug and above in release) to every feedback submission. That is
   defensible and probably necessary, but the feedback screen's copy does not mention it, and the
   privacy policy will have to. Users generally read "send feedback" as "send my message".
3. **The paywall's Restore purchases button may be unreachable enough to fail App Review.**
   `PaywallTrigger.Direct` exists (`RealPaywallCoordinator.kt:66`) and no UI ever requests it;
   `PaywallNavigator.kt:48` only opens the paywall from a coordinator offer, and
   `PaywallFeatureEntryPoint.kt:54` from an offline block. Settings has no Sodogku Pro row. Apple
   Guideline 3.1.1 expects a restore mechanism a user can find. A Settings entry that opens the
   paywall with `PaywallTrigger.Direct` would close it.
4. **Dead auth-era rate limits on the server.** `apps/server/.../plugins/RateLimits.kt` still
   registers `DELETE_ACCOUNT_LIMIT` and `PLAYER_REPORT_LIMIT`, with comments about App Store review
   of a deletion endpoint. Both endpoints were deleted in C0.
5. **Supabase references survive in shipped manifests.** `AndroidManifest.xml`'s deep-link comment
   and `apps/ios/iosApp/Info.plist`'s `CFBundleURLTypes` comment both describe
   `sodogku://auth/confirmed` and `sodogku://login-callback` as Supabase OAuth return trips.
   Comments only, but a store reviewer reading the manifest sees an auth flow the app does not have.
6. **`Telemetry.setUser` is a loaded gun.** It is implemented, reachable, and would put an email
   into Sentry the moment anyone calls it, invalidating §1 and most of §5. Worth a comment on the
   interface saying so, or deleting it since there are no accounts.
