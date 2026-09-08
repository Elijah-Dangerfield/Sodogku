# Things only you can do

Work blocked on a person with credentials, a browser, a password, a payment
method, or a file. Nothing here can be done by an agent, which is why it is
separate from `todos.md` (the queue a worker takes from) and from
`BUILD-PLAN.md`'s punch list (features waiting to be built).

Ordered by what blocks shipping soonest. Delete an item when it is done; if one
turns out not to be needed, delete it and say why in the commit.

Everything below was verified against the code on 2026-09-08. Where a value is
a placeholder or a public test credential, it says so.

---

## 1. Create the GitHub repository

**Blocks every other item that involves CI, and breaks the app's legal links.**

`git remote -v` returns nothing. There is no `origin`. No workflow has ever
run, no secret can be added until the repo exists, and GitHub Pages is not
publishing, so these two URLs 404 today:

```
https://elijah-dangerfield.github.io/Sodogku/privacy.html
https://elijah-dangerfield.github.io/Sodogku/terms.html
```

Those are the Terms and Privacy links inside the app. They are hardcoded as
config defaults at `libraries/config/.../LegalConfigValues.kt:35,58`, so **the
owner and repo name are load-bearing**: create it as exactly `Sodogku` under
`elijah-dangerfield`, or the in-app links and the store listings that must
match them break silently.

Then: Settings → Pages → Source → **GitHub Actions**.

Note that `SETUP.md:110` and `docs/release-automation.md:205` both say to
publish from `main` / `/pages`. That is wrong and GitHub no longer offers it;
`.github/workflows/pages.yml:10` uses `actions/deploy-pages`, which requires
the Actions source. Those two docs need correcting, which an agent can do.

---

## 2. Two paid developer accounts

- **Apple Developer Program**, $99/year. Precondition for the App Store Connect
  record, Game Center, In-App Purchase, and every signing secret below.
- **Google Play Console**, $25 once. Precondition for everything in item 6.

---

## 3. Rewrite the privacy policy, because the shipped one is false

**Would fail review at both stores.**

`pages/privacy.html:37` currently claims the app does not:

> Use advertising or analytics SDKs (no Google Analytics, no Facebook SDK, no ad networks).

That is inherited template text and it is not true. The app ships AdMob, the
UMP consent SDK, App Tracking Transparency, and a Grafana Cloud log pipe:

- `libraries/ads/impl/src/androidMain/.../AdMobAdNetwork.kt:134` — `UserMessagingPlatform`
- `apps/ios/iosApp/Info.plist:37` — `NSUserTrackingUsageDescription`
- `libraries/telemetry/impl/.../GrafanaAppEvents.kt:139-151` — Grafana Cloud

Both stores require the policy to accurately describe what is collected.
`docs/store/data-safety.md` is the drafted input for a correct one, and an
agent can write the replacement text. **You have to read and own it before it
goes live**, which is the part that belongs here.

`pages/terms.html` carries the same template date but its content is generic
enough to stand.

---

## 4. Re-export the iOS app icon without transparency

**App Store Connect rejects the upload (ITMS-90717).**

```
$ sips -g all "apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/Sodogku Icon-selection (7).png"
  pixelWidth: 1024
  pixelHeight: 1024
  hasAlpha: yes
```

Apple requires a flattened, opaque, full-bleed 1024x1024 with square corners.
The current file has an alpha channel and pre-rounded corners. Apple draws the
rounding itself.

Also still missing and owner-supplied:

- **Play feature graphic**, 1024x500. Mandatory, does not exist.
- **Play listing icon**, 512x512.
- **iOS screenshots** at 6.9". Blocked on item 12. The eight Android frames in
  `docs/store/screenshots/android-phone/` exist and must not be submitted as
  iPhone screenshots.

---

## 5. App Store Connect and the Apple developer portal

The bundle ID is **`com.sodogku.Sodogku`**, not `com.sodogku`
(`apps/ios/iosApp.xcodeproj/project.pbxproj:264`, `apps/ios/fastlane/Appfile:1`).
Android uses `com.sodogku` (`versions.properties:1`). The two stores get
different identifiers. `docs/SPEC.md:1485` says otherwise and is stale.

**App Store Connect:**

- Create the app record for `com.sodogku.Sodogku`.
- Two **Classic** Game Center leaderboards under Features → Game Center:

  | Reference name | Leaderboard ID | Format | Sort | Range |
  |---|---|---|---|---|
  | Lifetime Score | `com.sodogku.leaderboard.lifetime_score` | Integer | High to Low | from 0 |
  | Longest Streak | `com.sodogku.leaderboard.longest_streak` | Integer | High to Low | 0 to ~3650 |

  **The IDs have to match exactly** (`libraries/leaderboards/.../Leaderboard.kt:59,62`).
  A mismatch fails silently and looks like a board nobody is on. Each needs a
  localisation and an image.

- A **non-consumable** with product id `sodogku_pro` (see item 7).
- App Privacy questionnaire, age rating, screenshots.

**developer.apple.com → Certificates, Identifiers & Profiles → the App ID:**

- Enable **Game Center**.
- Enable **In-App Purchase**.

Xcode's automatic signing usually does both the first time it provisions. A
manual profile has to be regenerated. If a device or TestFlight build suddenly
fails to sign, this is why.

The app side of leaderboards is live as of `647d4a8`: the module is in the
graph and Settings shows a Leaderboards row on iOS once Game Center answers.
Until the records exist, submissions go out and are dropped by the platform,
which is the designed behaviour and produces no error anywhere.

---

## 6. Google Play Console

Package `com.sodogku`, read from `versions.properties:1` by
`.github/workflows/release.yml:145-152`.

- Create the app.
- **Generate the upload keystore** and keep it somewhere you will not lose it.
  Command at `docs/release-automation.md:158-161`. Losing it means never being
  able to update the listing.

  Worth knowing: `ApplicationConventionPlugin.kt:111` falls back to the *debug*
  key when release signing credentials are absent, with only a warning
  (`Signing.kt:27-33`). CI catches that with an explicit precheck
  (`release.yml:155-164`); a local release build does not.

- Enrol in **Play App Signing**.
- Create a **service account JSON** with the Release manager role. Six steps at
  `docs/release-automation.md:174-180`.
- Store listing, content rating, target audience, **data safety form**,
  category, contact details.
- **Ship the first production release by hand.** The upload action cannot push
  to production until an approved production release exists.
  `release.yml:185-215` already detects this and routes the first one to
  `internal`.

---

## 7. AdMob: nothing exists yet, and the app ships Google's test IDs

Every ad identifier compiled into the app today is a Google-published sample,
labelled as such in source:

| Value | Where | What it is |
|---|---|---|
| `ca-app-pub-3940256099942544/5224354917` | `AdUnits.kt:43` | Android rewarded **test** unit |
| `ca-app-pub-3940256099942544/1712485313` | `AdUnits.kt:51` | iOS rewarded **test** unit |
| `ca-app-pub-3940256099942544~3347511713` | `AndroidManifest.xml:76` | Android **test** app id |
| `ca-app-pub-3940256099942544~1458002511` | `Info.plist:30` | iOS **test** app id |
| `""` | `AdUnits.kt:59`, `:64` | the real units, unset |
| `useTestUnits = true` | `AdUnits.kt:39` | the switch, still on |

**Rewarded only, which matches the decision that ads pay for bones and sniffs
and nothing else.** `AdNetwork.kt:22-24` declares exactly one `AdFormat`. So:
two AdMob apps, one per platform, one rewarded unit each. **Four values.**

Real ones look like `ca-app-pub-<16 digits>~<10 digits>` for an app id and
`ca-app-pub-<16 digits>/<10 digits>` for a unit id.

The **app id has to go into both native files** as well, because both SDKs read
it before any Kotlin runs (`AdUnits.kt:26-29` explains why it cannot live with
the unit ids). On Android a missing key crashes the app at startup and a wrong
one silently serves nothing.

Also in the AdMob console: set the **GDPR message** under Privacy & messaging.
Without it the UMP form has nothing to display in the EEA and
`isConsentFormAvailable` is false at `AdMobAdNetwork.kt:136`.

Two related gaps:

- **No `app-ads.txt`.** `pages/` does not have one. AdMob wants it on the
  developer website named in the listing to authorise sellers. Missing it
  depresses fill rate.
- **iOS serves no ads at all.** The Google Mobile Ads SDK is not in the Xcode
  project: `project.pbxproj:398` has exactly one package reference,
  `sentry-cocoa`. Every ad path is behind `#if canImport(GoogleMobileAds)`
  (`Platform/AdNetwork.swift:42-47`), so on iOS the shared Kotlin grants every
  reward for free. Adding the SPM dependency is an agent's job, not yours, but
  it is a hard revenue blocker and belongs in the same conversation.

---

## 8. In-app purchase: one product, same ID on both stores

```kotlin
// libraries/billing/.../StoreBilling.kt:95-98
object ProductIds {
    const val pro: String = "sodogku_pro"
}
```

- **Play Console:** a **managed product** with id `sodogku_pro`.
- **App Store Connect:** a **non-consumable** with id `sodogku_pro`.

Intended price $4.99 (`docs/SPEC.md:603`). Never hardcode it; the runtime price
comes from the store (`StoreBilling.kt:80-83`).

Nothing crashes without them. The paywall opens, the button reads "Get Pro"
with no price, and pressing it says "The store is not available on this
device." On iOS a missing product makes ownership checks return `.unknown`
rather than `.notOwned`, deliberately, so restore reports that it could not
reach the store.

There is no `.storekit` configuration file in the repo. Setup steps are in the
header of `apps/ios/iosApp/Platform/StoreBilling.swift:12-24`.

---

## 9. Save the art you have described into the repo

**Blocking four punch-list items,** including the welcome screen having any
background at all.

**Pasting an image into a chat does not put it on disk.** That is the only
reason this item has outlived several attempts to close it.

- **The three paw-and-bone backgrounds** shown on 2026-09-08.
  `art/source/backgrounds/README.md` names the files and says where each goes.
  They need **transparent** backgrounds, not white, since the screens behind
  them are cream.
- **A flame icon** for the streak badge. It currently draws a paw print, which
  works. Swapping it is one line in `StreakButton.kt` once `Icons.Flame` exists.
- **The sad dog and the bone artwork** mentioned earlier.

Originals go in `art/source/`. Anything the app renders also goes in
`libraries/resources/src/commonMain/composeResources/drawable/` as lowercase
snake_case; Compose generates `Res.drawable.<name>` from the filename.

If the PNGs turn out awkward (tiling seams, wrong density on a tall screen),
say so: `drawPaw` and `drawBone` already exist as vector code in
`GameShapes.kt`, so the background can be drawn in code instead, where density
and fade are parameters rather than a re-export.

---

## 10. GitHub secrets and variables

All of these are undone, because there is no repo yet. `GITHUB_TOKEN` is
automatic and needs nothing.

**Secrets** (Settings → Secrets and variables → Actions → Secrets):

| Secret | Blocks |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | any Android store build |
| `ANDROID_KEYSTORE_PASSWORD` | same |
| `ANDROID_KEY_ALIAS` | same |
| `ANDROID_KEY_PASSWORD` | same |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play upload (skipped with a warning if blank) |
| `APPLE_TEAM_ID` | all iOS signing, hard fail |
| `ASC_KEY_ID` | same |
| `ASC_ISSUER_ID` | same |
| `ASC_KEY_P8_BASE64` | same |
| `APPLE_DIST_CERT_P12_BASE64` | optional, but without it Xcode mints a new distribution cert per run and hits Apple's cap of 2-3 |
| `APPLE_DIST_CERT_PASSWORD` | pairs with the above |
| `SENTRY_DSN` | crash reporting stays dormant |
| `SENTRY_AUTH_TOKEN` | mapping and dSYM upload skipped |
| `GRAFANA_OTLP_BASE_URL` | all three blank leaves telemetry dormant |
| `GRAFANA_OTLP_INSTANCE_ID` | same |
| `GRAFANA_LOGS_WRITE_TOKEN` | same |
| `FLY_API_TOKEN_DEV` | dev server deploy |
| `FLY_API_TOKEN_PROD` | prod server deploy |
| `ADMIN_API_TOKEN_DEV` | optional, skips config manifest upload |
| `ADMIN_API_TOKEN_PROD` | same |

**Variables**, not secrets, which is easy to get wrong: `SENTRY_ORG`,
`SENTRY_PROJECT`. Read via `vars.` at `release.yml:127-128`.

**Repository settings:** enable Actions; Pages source = GitHub Actions; create
an Environment named `production` with a required reviewer, or
`server-deploy-prod.yml:56` deploys unguarded.

`SETUP.md:38-113` and `docs/release-automation.md:140-215` document how to
obtain each one.

---

## 11. Sentry

The DSN resolves at `build-logic/.../Versioning.kt:190` as env `SENTRY_DSN`,
then `local.properties` key `sentry.dsn`, then blank. `local.properties` today
holds only `sdk.dir`, so every local build bakes a blank DSN and
`SentryRuntimeConfig.isEnabled` is false. That is deliberate: a fresh clone
works with no setup.

Beyond the DSN you need a `SENTRY_AUTH_TOKEN` with scopes
`org:read project:read project:write project:releases`, plus the org and
project slugs as **variables**. Without the token, Android R8 mapping upload is
skipped and every crash frame arrives as `a.b.c`; iOS dSYM upload is skipped
too.

This also unblocks proving the feedback loop end to end. The in-app panel works
and the tag reaches telemetry (logcat confirms
`Sentry disabled, feedback dropped | tags={feedback_kind=owner_directive}`).
What is unproven is everything past `captureUserFeedback`. The `feedback-triage`
skill queries Sentry for those tags, so it cannot be exercised until reports
land.

---

## 12. Run one command in a terminal

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

`xcode-select -p` already reports this path and iOS builds work here, but the
simulator integration refuses with an error naming this exact command. It needs
your password, so it cannot be run from a session.

Without it, iOS can be built, installed, launched and screenshotted, but **not
tapped**. Every iOS gesture so far is unverified, including the feedback
panel's swipe and anything behind a Game Center sign-in. It also blocks the
6.9" screenshots in item 4.

---

## 13. Grafana Cloud, for product analytics

Three values, all blank today, from Grafana Cloud → OpenTelemetry:
`GRAFANA_OTLP_BASE_URL`, `GRAFANA_OTLP_INSTANCE_ID`, `GRAFANA_LOGS_WRITE_TOKEN`.
The token needs `logs:write`.

`GrafanaCloud.isConfigured` requires all three
(`GrafanaAppEvents.kt:144-145`), and when false the OTLP log tree is never
planted. Events still reach logcat and Sentry, so **nothing looks broken**. You
simply have no analytics.

`SETUP.md:87` warns that Grafana auto-revokes `glc_` tokens it finds in public
repos.

---

## 14. Fly.io and Supabase, for remote config

Nothing serves remote config today. The client falls back to compiled defaults
in `FallbackConfigMap.kt` and stays fully playable, by design. This is the
least urgent item here for that reason.

- Two Fly apps, created by hand: **`sodogku-server-dev`** and
  **`sodogku-server-prod`**. The suffix is load-bearing: the environment tag is
  derived from `FLY_APP_NAME` at `ServerConfig.kt:224-225`.
- One **Supabase project per environment** for `DATABASE_URL`. Steps at
  `apps/server/DEPLOY.md:24-48`.
- Fly secrets per app: `DATABASE_URL`, `ADMIN_API_TOKEN`
  (`openssl rand -hex 32`), optionally `SUPABASE_URL`,
  `SUPABASE_SERVICE_ROLE_KEY`, `SENTRY_DSN`, `APPEAL_URL`.

Failure is silent: with no `DATABASE_URL` the server starts in limited mode and
`/v1/app-config` is simply not mounted.

The **admin console** (`apps/admin`) is served by each server at `/admin`. No
credential is baked in; you paste the environment's `ADMIN_API_TOKEN` into the
browser at runtime.

One blocker here is a code fix rather than a credential, but you have to pick
the host first: `DefaultNetworkConfig.kt:18` is `baseUrl = ""` and nothing
overrides it, so even a deployed server is unreachable by the client.

---

## 15. Decisions I need from you

- **Kids category or general audience.** `docs/SPEC.md:1543-1548`, and the
  expensive one. It gates both stores' privacy questionnaires, not just the ad
  model. General audience is what the code already implements. The Apple Kids
  Category branch removes AdMob, Sentry and Grafana together and takes the
  entire rewarded-ad economy with them.
- **A support email.** Both stores require a support contact and the app has
  none. `docs/store/listing.md:169` records it as undecided.
- **How a player asks for data deletion.** Play's form asks directly and it is
  currently unanswerable (`SPEC:1549-1553`).
- **A weekly streak present.** Not built. With ads narrowed to bones and sniffs
  it is pure cost with no impression behind it, and the streak already pays out
  twice through the freeze and the restore. If you want one, the suggestion is
  3 bones on day 7: one board's worth of mistakes, denominated in what the ads
  already sell.
- **`com.apple.developer.applesignin`** is still in `iosApp.entitlements:5-8`
  from identity work that was deleted, and nothing enables it. Removing an
  entitlement is safe where adding one is not, but it deserves a deliberate
  decision rather than a drive-by removal.
- **The Supabase project ref `mfozvowjsxdwrslyoyrf`** is hardcoded at
  `Versioning.kt:136` and baked into every build. Nothing reads it:
  `SupabaseInfo.kt` has zero call sites and there is no Supabase client. It
  points at a project you may not own, and it goes live the moment anything
  reads it. Replace it or delete it.

---

## Checked and genuinely not needed

Stated so nobody adds them by reflex.

- **Push notification certificates, APNs, Firebase.** No FCM, no
  `google-services.json`, no Firebase dependency. The app posts no
  notifications.
- **A separate analytics account.** Grafana Cloud is the pipe. No Google
  Analytics, Amplitude or Firebase Analytics.
- **A support Discord or channel.** A support *email* is needed; a channel is
  not.
- **A custom domain.** GitHub Pages satisfies the privacy policy URL
  requirement. You will want one only if you use the developer website field,
  which is where `app-ads.txt` would live.
- **Remote config keys for the ad units or the product id.** Deliberately kept
  out of config (`AdUnits.kt:16-18`, SPEC 4.4): changing one is a store
  operation, and a config outage that blanked them would take ads and purchases
  down together.
