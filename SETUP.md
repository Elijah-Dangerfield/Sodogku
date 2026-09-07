# Setup checklist — Sodogku

Action items after running `./scripts/init_project.main.kts`. Work through these in order. Tick off as you go.

**Hour 1 — a running app:**
- [ ] [Local dev](#local-dev) — hooks + first build
- [ ] [Supabase auth](#supabase-auth-hour-1) — project, providers, redirect URLs
- [ ] [Server deploy](#server-deploy-flyio) — dev Fly app + secrets + `/_health`

**Day 1 — pipelines + visibility:**
- [ ] [GitHub secrets](#github-secrets-only-if-you-enabled-ci) (only if you enabled CI)
- [ ] [Repo settings](#repo-settings) — Pages, Actions, branch protection, the `production` Environment
- [ ] [Day-1 verification](#day-1-verification) — prove telemetry + deploys actually work

**Before shipping:**
- [ ] [Store listings](#store-listings) — Play Console + App Store Connect
- [ ] [First release](#first-release) — the manual-promotion gotcha
- [ ] [App icons](#app-icons)

---

## Local dev

```sh
./scripts/install_hooks.sh   # commit-msg hook for Conventional Commits
./gradlew build              # first sync + build
```

The Gradle build fails with an install-hooks message if you skip `install_hooks.sh`. That's intentional — release-please derives version bumps from commit history, so every commit must be in Conventional-Commits form (`feat:`, `fix:`, etc.).

To bypass in scripted contexts (not CI — `CI` env var is honored): `-Dsodogku.skipGitHooksCheck=true`.

---

## GitHub secrets (only if you enabled CI)

Set under **Settings → Secrets and variables → Actions**. All are required for `release.yml` to ship.

### Android signing

| Secret | How to get it |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -i upload-keystore.jks \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console → Setup → API access → create service account with *Release apps to testing tracks* + *Release apps to production*. Download the JSON. |

### Apple signing + App Store Connect

| Secret | How to get it |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer → Membership → Team ID |
| `ASC_KEY_ID` | App Store Connect → Users and Access → Keys → Key ID |
| `ASC_ISSUER_ID` | Same page — Issuer ID (top of the Keys tab) |
| `ASC_KEY_P8_BASE64` | `base64 -i AuthKey_XXX.p8 \| pbcopy` |
| `APPLE_DIST_CERT_P12_BASE64` | Export your Apple Distribution cert from Keychain as .p12, then `base64 -i dist.p12 \| pbcopy` |
| `APPLE_DIST_CERT_PASSWORD` | Password you set when exporting the .p12 |
| `FASTLANE_APPLE_ID` *(optional)* | Apple ID email, for `fastlane deliver` |

### Sentry

| Secret | Notes |
| --- | --- |
| `SENTRY_AUTH_TOKEN` | Sentry → User Auth Tokens → scope: `project:releases`, `org:read`. Used by `beta.yml`/`release.yml` to create releases + upload mappings/dSYMs. |
| `SENTRY_DSN` | Sentry → Project Settings → Client Keys (DSN). Baked into store builds so crash reporting is live; blank leaves crash reporting dormant. |

And under **Settings → Secrets and variables → Actions → Variables** (not secrets):

| Var | Value |
| --- | --- |
| `SENTRY_ORG` | Your Sentry org slug |
| `SENTRY_PROJECT` | Your Sentry project slug |

### Grafana Cloud telemetry (optional)

Used by `beta.yml` and `release.yml` to bake client app-event credentials into
store builds. Leave unset and the telemetry pipe stays dormant — the app builds
and runs fine.

| Secret | Notes |
| --- | --- |
| `GRAFANA_OTLP_BASE_URL` | Grafana Cloud → OpenTelemetry → OTLP endpoint base URL |
| `GRAFANA_OTLP_INSTANCE_ID` | Same page — instance id (the numeric user) |
| `GRAFANA_LOGS_WRITE_TOKEN` | A Grafana Cloud access-policy token with logs:write. Grafana auto-revokes `glc_` tokens it finds in public repos — never commit one. |

### Server deploy (Fly.io)

`server-deploy.yml` auto-deploys the dev server on pushes to `main` that touch
server paths; `server-deploy-prod.yml` queues a prod deploy behind a manual
approval. Requires the two Fly apps from `apps/server/DEPLOY.md`.

| Secret | Notes |
| --- | --- |
| `FLY_API_TOKEN_DEV` | `fly tokens create deploy -a <project>-server-dev --expiry 8760h` |
| `FLY_API_TOKEN_PROD` | `fly tokens create deploy -a <project>-server-prod --expiry 8760h` |
| `ADMIN_API_TOKEN_DEV` *(optional)* | The dev server's `ADMIN_API_TOKEN` — lets the deploy upload the per-version config manifest (see `apps/admin/README.md`). Skipped with a warning if unset. |
| `ADMIN_API_TOKEN_PROD` *(optional)* | Same, for prod. |

Also create the **`production` GitHub Environment** (Settings → Environments →
New environment → `production` → Required reviewers → add yourself).
`server-deploy-prod.yml` pauses on it until a human approves the run — without
the environment the prod deploy runs unguarded.

---

## Repo settings

- **Actions** → enable workflows.
- **Pages** → Source: `Deploy from a branch`, Branch: `main` / folder: `/pages`. (The `pages.yml` workflow can also publish on push.)
- **Branch protection** on `main`:
  - Require PR.
  - Require status checks: `CI / Build + test`, `commitlint / Validate PR title`.
  - Require linear history (so release-please squash-merges cleanly).

---

## Store listings

Before `release.yml` can ship:

1. **Play Console** → Create app → fill out store listing, data-safety form, content rating, pricing/distribution. Create at least one internal track tester.
2. **App Store Connect** → My Apps → New App → pick the bundle ID that matches `apps/ios/fastlane/Appfile`. Fill out app info, pricing, privacy details. Note: Apple checks the binary's bundle name / display name for uniqueness at *delivery* time (ITMS-90129), not here — if your app name is a common word, the first upload may bounce; pick a more distinctive `CFBundleName`/`CFBundleDisplayName` in `apps/ios` and re-upload.
3. **TestFlight** external group: create a group named `External Testers` (or change `TESTFLIGHT_EXTERNAL_GROUP` in `release.yml`).
4. Privacy policy + terms of service URLs — the `pages/` folder generates these; once Pages is enabled they're at `https://<you>.github.io/<repo>/privacy.html` etc. Paste the URLs into both store listings.

---

## First release

> **!!! READ THIS BEFORE YOUR FIRST RELEASE !!!**
>
> Google Play and Apple both reject automated production uploads until a manually-promoted build exists. `release.yml` detects this and routes the **first** release to:
>
> - **Play internal track** (not production).
> - **TestFlight internal** (not external, not App Store submission).
>
> You must then, **once**:
>
> 1. **Play Console** → Internal testing → Promote release → Production. Fill out the production rollout form manually.
> 2. **App Store Connect** → TestFlight build → Submit for review manually.
>
> From release #2 onward, the pipeline uploads straight to Play production (10% staged rollout) and submits to the App Store with phased release.

The release-please PR shows a `!!! FIRST RELEASE !!!` banner the first time around so this is hard to miss.

---

## App icons

Drop your icons into:

- **iOS** → `apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/` (replace the placeholder set).
- **Android** → `apps/compose/src/androidMain/res/mipmap-*/` (replace `ic_launcher*.webp`).
- **Shared** (used by Compose splash, about screens, etc.) → `libraries/resources/src/commonMain/composeResources/drawable/`.
- **GitHub Pages** → `pages/app-icon.png`, `pages/favicon.png`, `pages/apple-touch-icon.png`.

---

## Networking

`:libraries:networking` ships a single configured `HttpClient` (plus an
authenticated variant) for every repo and data source to share.

**Set your base URL.** Bind your own `NetworkConfig` (see
`DefaultNetworkConfig`) somewhere in your app — typically a class that reads
the URL from BuildConfig per build variant:

```kotlin
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultNetworkConfig::class])
@Inject
class AppNetworkConfig : NetworkConfig {
    override val baseUrl = AppBuildConfig.API_BASE_URL
}
```

**Authenticated calls.** Inject `NetworkClient` and use
`networkClient.authenticatedClient` for endpoints that need a Bearer token.
The token comes from your `AuthTokenProvider` binding (default is no-op).
401s trigger `refreshAccessToken()`.

**Wrap calls with `Catching { }`** at the call site. Ktor throws on non-2xx
and network errors; the rest of the codebase already uses this pattern.

**JSON config.** `NetworkJson` is strict in debug (unknown keys/missing
fields throw) and lenient in release (so a backend tweak can't crash users).

## Supabase auth (hour 1)

The app ships with anonymous-first Supabase auth wired end to end (guest
creation in onboarding, email/password + Apple + browser-OAuth sign-in,
encrypted session storage, `/v1/me` profile). To light it up:

1. Create a Supabase project (free tier is fine). Note the project URL and
   the **publishable (anon) key** (Settings → API Keys).
2. Client config — add to `local.properties` (or export as env vars in CI):
   ```
   supabase.projectId=<ref>
   supabase.url=https://<ref>.supabase.co
   supabase.anonKey=<publishable key>
   ```
3. Enable providers in the Supabase dashboard (Authentication → Providers):
   **Anonymous sign-ins** (required for the guest flow), **Email**
   (confirm-email on), and optionally **Apple** / **Google**.
4. Redirect URLs (Authentication → URL Configuration): add your custom
   scheme callbacks so browser OAuth and the verify-email link return to
   the app:
   ```
   <yourscheme>://login-callback
   <yourscheme>://auth/confirmed
   ```
   The scheme is your project's lowercase name (see the intent filter in
   `AndroidManifest.xml` / `CFBundleURLTypes` in `Info.plist` — both already
   enabled).
5. Server env (see `apps/server/.env.example`): `SUPABASE_URL` for JWT
   verification, and `SUPABASE_SERVICE_ROLE_KEY` if you want in-app account
   deletion (`DELETE /v1/me`) and display-name mirroring. Treat the service
   role key as a root password — server secrets only, never the client.
6. Verify: launch the app → complete onboarding as a guest → a user appears
   in Supabase → Authentication → Users with `is_anonymous = true`, and
   `GET /v1/me` (through the app) creates the profile row.

## Deep links

Compose NavHost handles the routing once URLs reach it. Per-route deep
links go on `screen<Route>(deepLinks = ...)` — use `routeDeepLink<T>()`,
never bare `navDeepLink` (iOS crashes on the missing base-route NavTypes).

The custom-scheme wiring is already enabled on both platforms (the auth
flows depend on it): the intent filter in
`apps/compose/src/androidMain/AndroidManifest.xml` and `CFBundleURLTypes`
in `apps/ios/iosApp/Info.plist`. For https App/Universal Links, add the
`assetlinks.json` / `Associated Domains` + AASA setup alongside.
`iOSApp.swift` forwards every `.onOpenURL` event to the Kotlin
`DeepLinkBridge`; `App.kt` routes OAuth callbacks to the auth layer and
everything else into the nav graph.

## In-app review

Inject `ReviewPrompter` and call `requestReview()` from a delighted-user
moment (e.g. after the user completes a meaningful task, or after N
sessions). The OS owns the throttling decision — both stores rate-limit how
often the dialog actually shows. Don't show your own UI before/after.

---

See `docs/release-automation.md` for the full pipeline runbook.

---

## Day-1 verification

Prove the observability + deploy story end to end while everything is fresh.
Each check has a definitive pass signal; if one fails, fix it now — these are
the tools you'll be debugging with later.

1. **Server is live.**
   ```sh
   curl https://<your-app>-server-dev.fly.dev/_health
   ```
   Expected: `{"ok":true}`.
2. **Client → server round trip.** Launch the app (device or simulator),
   complete onboarding as a guest. Expected: a new user in Supabase →
   Authentication → Users with `is_anonymous = true`, and a row in the
   `profiles` table.
3. **Find your session in Loki** (if Grafana is wired). In Grafana → Explore →
   Loki, query your client logs by the app's service name and filter
   `session_id="<id>"` — grab the id from the app's debug shake dialog or
   logcat (`Session started`). Expected: the `app.launched` event and your
   request logs, and the SAME `session_id` on the server's request logs.
4. **Trigger a test crash → Sentry.** Debug builds: shake → QA dialog → the
   test-crash affordance (or add a temporary `error()` behind a button).
   Expected: the event in Sentry within a minute, tagged with `session_id`,
   `commit_sha`, and a trace link that opens Tempo.
5. **Config round trip.** Open the admin console (`/admin` on the dev
   server, paste your `ADMIN_API_TOKEN`), flip `upgrade.maintenanceMessage`
   to a test string, foreground the app twice (refresh is throttled).
   Expected: the value changes in the QA config dashboard; the audit tab
   records your change.
