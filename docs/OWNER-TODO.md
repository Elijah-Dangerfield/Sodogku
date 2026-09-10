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

## 1. Two paid developer accounts

- **Apple Developer Program**, $99/year. Precondition for the App Store Connect
  record, Game Center, In-App Purchase, and every signing secret below.
- **Google Play Console**, $25 once. Precondition for everything in item 6.

---

## 2. Read and own the privacy policy and the terms

**Both stores link to these pages, and both are live.**

`pages/privacy.html` was rewritten against the code in `1ec24e0`; every
statement traces to a file. `pages/terms.html` gained sections on ads, Sodogku
Pro, and in-game items on 2026-09-10, written from `StoreBilling.kt`,
`AdNetwork.kt`, the paywall strings and SPEC §5 and §6. An agent wrote both.
A person has to read them end to end and accept them as their own, and that
person is you. Read them in a browser at the Pages URL rather than in the
editor, since that is what a reviewer sees.

Three things in the privacy policy were deliberate choices rather than
factual checks, and you should know they were made:

- **The opening line no longer says the app is unreleased.** It now says the
  page describes the build current on the "Last updated" date, which is true
  before and after launch, so nothing goes stale on launch day.
- **Deletion requests are routed through the in-app feedback form**, not
  email. The only key on any record is the install id, the app never shows
  it, and an email cannot be matched to anything. A feedback report, though,
  reaches Sentry tagged with `install_id` (`AppTelemetry.kt`, `setInstallId`),
  so the policy tells players to ask there. That is a real route today with no
  code change, and it is what lets Play's "can users request deletion" be
  answered yes. Whether it is enough is the decision below.
- **Analytics have no in-app opt-out**, and the page says so plainly rather
  than implying a choice the player does not have.

**Decision: how visible should the install id be?** The policy commits us to
serving a deletion request that arrives by feedback. Three ways to go from
here, in increasing cost; none of them is chosen, because the choice sets a
support expectation only you can carry:

1. **Leave it as written.** Feedback form is the route. Cost: nothing to
   build. Risk: a player who emails instead gets told to go back into the app,
   and a player who has already uninstalled has no route at all (their id is
   gone with the install, which is also the argument that nothing links them
   any more).
2. **Show the id in Settings**, an "About" row with the UUID and a copy
   action, and say in the policy that a player may quote it by email. Cost:
   one row in `features/settings/impl`, one string, and the policy paragraph.
   Risk: small; it is a random UUID with nothing behind it. It does give a
   player something that looks like an account number for an app that has no
   accounts, which is a copy problem more than a privacy one.
3. **Add an in-app "share usage data" switch** wired to the same gate as
   `telemetry.appEventsEnabled`. Cost: real. It changes the Data safety
   answers (the install id row stops being "required"), it needs the switch to
   also stop the Sentry tag and the `X-Install-Id` header or it is a half
   truth, and it removes the diagnostics you will want most in the first
   month. Not recommended before launch.

Whichever you pick, the deletion paragraph in `pages/privacy.html` and the
Play Data safety answer have to say the same thing. Also note SD-29 in
`docs/todos.md`: the `install_id` tag is set opportunistically at cold boot,
and until that is fixed a report filed in the first seconds after a cold start
can arrive without it. That does not change the decision, but it is why the
route is "a real route" and not yet "a guaranteed one".

**Versioning:** `legal.termsVersion` and `legal.privacyVersion` are both still
`1` in `FallbackConfigMap.kt`. Nobody has accepted a version yet, so these
edits do not need a bump. The first change after launch does, through remote
config, per `LegalConfigValues.kt`.

---

## 3. Re-export the iOS app icon without transparency

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

## 4. App Store Connect and the Apple developer portal

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

## 5. Google Play Console

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

## 6. AdMob: nothing exists yet, and the app ships Google's test IDs

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

## 7. In-app purchase: one product, same ID on both stores

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

## 8. Save the art you have described into the repo

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

## 9. GitHub secrets and variables

The repo exists and is public as of 2026-09-08, and Pages is live, so the app's
Terms and Privacy URLs resolve. Everything below is still unset.
`GITHUB_TOKEN` is automatic and needs nothing.

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

## 9b. Update the two store privacy forms when the answer sheet changes

Small, recurring, and easy to forget because it feels like a one-off.

`docs/store/data-safety.md` is the derived answer sheet for Play's **Data
safety** form and Apple's **App Privacy** nutrition label. Both forms have to be
updated whenever what the app collects changes; neither is filed once. Agents
keep the file true (that is `SD-7` in `docs/todos.md`); only you can edit the
forms.

**So:** when a commit says a row in `data-safety.md` changed, go and change the
matching field in both consoles. Play: Policy → App content → Data safety.
Apple: App Store Connect → your app → App Privacy.

Worth knowing which changes actually move a row, since most do not: a new SDK, a
new `logEvent` attribute, and anything that starts calling `Telemetry.setUser`.

---

## 10. Sentry

**Done on 2026-09-10**, on this machine and in CI: `local.properties` carries a
DSN, both secrets and both variables are set, and a debug build was seen logging
`Sentry initialized for dev-android-debug`. Feedback has been round-tripped end
to end. What follows is only for a second machine or a fresh clone.

**One command.** The project (`elijah-dangerfield` / `sodogku`) and its DSN
exist; nothing here needs code:

```bash
./scripts/setup_sentry.sh
```

It prompts for an auth token, checks it, then writes `sentry.dsn` into
`local.properties` and sets the two repo secrets and two repo variables. Mint
the token at `https://elijah-dangerfield.sentry.io/settings/auth-tokens/` as an
**Organization Token**. Its scopes are fixed (`org:ci`: source map upload,
release creation, code mappings) and that is exactly the set CI needs, so there
is nothing to choose.

Why each piece matters, if you want to do it by hand instead. The DSN resolves
at `build-logic/.../Versioning.kt:190` as env `SENTRY_DSN`, then
`local.properties` key `sentry.dsn`, then blank; blank leaves
`SentryRuntimeConfig.isEnabled` false, which is why a fresh clone works with no
setup. Without the auth token, Android R8 mapping upload is skipped and every
crash frame arrives as `a.b.c`; iOS dSYM upload is skipped too. The org and
project go in as **variables, not secrets**. `release.yml:127-128` reads them
via `vars.`, and getting that wrong fails silently.

**Not covered:** the server's own `SENTRY_DSN`, which is a Fly secret on a
separate deployment (§ the Fly secrets list above). Point it at a second Sentry
project rather than this one; server and client share no release string and
mixing them makes both harder to read.

This also unblocks proving the feedback loop end to end. The in-app panel works
and the tag reaches telemetry (logcat confirms
`Sentry disabled, feedback dropped | tags={feedback_kind=owner_directive}`).
What is unproven is everything past `captureUserFeedback`. The `feedback-triage`
skill queries Sentry for those tags, so it cannot be exercised until reports
land.

---

## 11. Run one command in a terminal

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

It also blocks the one thing about ads that actually matters to a player:
nobody has watched a rewarded ad on iOS and seen the bones arrive afterwards.
The SDK work is done and `xcodebuild` is green, so this is the last unproven
step. Worth pulling the network mid-ad while you are there, because the reward
is supposed to land anyway and on iOS that is currently untested.

---

## 12. Grafana Cloud, for product analytics

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

## 13. Fly.io and Supabase, for remote config

Nothing serves remote config today. The client falls back to compiled defaults
in `FallbackConfigMap.kt` and stays fully playable, by design. This is the
least urgent item here for that reason.

- Two Fly apps, created by hand: **`sodogku-server-dev`** and
  **`sodogku-server-prod`**. The suffix is load-bearing: the environment tag is
  derived from `FLY_APP_NAME` at `ServerConfig.kt:224-225`.
- One **Supabase project per environment** for `DATABASE_URL`. Steps at
  `apps/server/DEPLOY.md:24-48`.
- Fly secrets per app: `DATABASE_URL`, `ADMIN_API_TOKEN`
  (`openssl rand -hex 32`), optionally `SENTRY_DSN` and `APPEAL_URL`. The server
  reads no Supabase credential any more; Supabase is only the Postgres host.

Failure is silent: with no `DATABASE_URL` the server starts in limited mode and
`/v1/app-config` is simply not mounted.

The **admin console** (`apps/admin`) is served by each server at `/admin`. No
credential is baked in; you paste the environment's `ADMIN_API_TOKEN` into the
browser at runtime.

One blocker here is a code fix rather than a credential, but you have to pick
the host first: `DefaultNetworkConfig.kt:18` is `baseUrl = ""` and nothing
overrides it, so even a deployed server is unreachable by the client.

---

## 14. Decisions I need from you

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
