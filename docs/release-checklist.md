# Release checklist

Everything between here and Sodogku being downloadable, in dependency order.
Tick items as they land and delete the file when the app has shipped twice,
because a checklist that outlives its release becomes a list of things nobody
remembers the reason for.

`OWNER-TODO.md` is the long form: why each item exists, what breaks without it,
and the exact console path. This file is only the order and the ownership.

**Who** is `you` for anything needing a password, a payment method or a
physical file, and `agent` for anything that is a change to this repo.

## Context, for anyone picking this up cold

Sodogku is a Kotlin Multiplatform puzzle game, Compose UI on both platforms,
free with ads and one non-consumable unlock called Sodogku Pro at $4.99.

| | |
|---|---|
| Android package | `com.sodogku` |
| iOS bundle id | `com.sodogku.Sodogku`, **not** `com.sodogku` |
| Play app | `4973873913912329622`, Nightjar Labs |
| App Store app | `6814528705`, in-app purchase `6814529334` |
| AdMob publisher | `pub-7008637445039253` |
| Release automation | release-please, then `.github/workflows/release.yml` |

Three release channels, set by `RELEASE_CHANNEL_OVERRIDE` and readable at
runtime as `BuildInfo.releaseChannel`: `dev` locally, `beta` from `beta.yml`
(TestFlight and Play internal), `store` from `release.yml`. Several items below
only apply to one of them.

Where the detail lives: `OWNER-TODO.md` for credentials and console work,
`todos.md` for the `SD-` queue, `docs/store/` for listing copy and the derived
privacy answers, `docs/reference/features.md` for what the app actually does.
Anything one of those asserts about a console is a claim as of a date, not a
live reading. Check the console before acting on it.

---

## Required

### 1. Port the template's setup scripts · agent

- [ ] `scripts/lib/setup_store.main.kts`, `scripts/setup_credentials.main.kts`
      and `scripts/setup_github_secrets.main.kts` from `KMPTemplate`, replacing
      `scripts/setup_sentry.sh`.
- [ ] `setup_fly.main.kts` and `setup_supabase.main.kts` alongside them, since
      they read the same store.

This is one step and it is first because everything else in this section either
needs a secret or needs CI. The template keeps one machine-local credential
store plus one signing folder outside every repo, so the Apple team, the Play
account and the Sentry org are entered once and every app reuses them. Fifteen
secrets then go up with a single command instead of being pasted in one at a
time.

### 2. Signing material · you

- [ ] Create the upload keystore into the shared signing folder.
- [ ] Enrol in Play App Signing.
- [ ] Create the Play service account JSON with the Release manager role.
- [ ] Run `./scripts/setup_github_secrets.main.kts`.

Losing the keystore means never being able to update the listing, so put the
signing folder somewhere backed up before anything else goes in it.

### 3. Flatten the iOS app icon · agent

- [ ] `apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/` reports
      `hasAlpha: no`.
- [ ] Same for `apps/compose/src/androidMain/ic_launcher-playstore.png`.

Must be done **before** step 4. Apple rejects a build carrying an icon with an
alpha channel (ITMS-90717), and it rejects it at upload, so an unflattened icon
turns the first release into two round trips.

### 4. Merge the release-please PR · you

- [ ] CI signs, builds and uploads to Play internal and to TestFlight.

The first Play upload has to be internal; `release.yml` already detects that
there is no approved production release and routes it there.

### 5. Create `sodogku_pro` in Play · you

- [ ] Managed product, id `sodogku_pro`, $4.99.

Blocked on step 4 and nothing else. Play does not offer the in-app products
page until a build carrying the billing permission exists. Until this is done,
Pro cannot be bought on Android at all. The App Store side is already complete.

### 6. Legal pages move, with a deletion form · agent, then you

- [ ] `legal/privacy.md` and `legal/terms.md` at the repo root, synced to
      `nightjarlabs.llc/sodogku/…` (SD-150).
- [ ] The studio site and the template both gain a **delete my data** form, so
      every Nightjar app has one URL that does this rather than each app
      inventing a route.
- [ ] You run `./scripts/setup_legal_sync.sh` once.
- [ ] You re-file four URLs: Play privacy policy, Play listing website, Play
      Data safety delete-data URL, Apple support URL.

The form is the part that earns this a place in Required rather than Suggested.
Play's Data safety form asks whether users can request deletion and the answer
was left blank because there was no route worth naming (`data-safety.md` §7.3).
A real form answers the question, replaces the current advice to file a feedback
report, and does it once for every app instead of once per app.

### 7. Ads consent revocation · agent

- [ ] SD-149: a Settings row that reopens the UMP privacy options form, shown
      only where the SDK reports it is required.

Google's EU consent policy expects a way to change the answer after the form has
been shown. The AdMob console flagged it when the GDPR message was published.

### 8. Flip the ad units, in the shipping release only · agent

- [ ] `AdUnits.useTestUnits = false`.

One line, and deliberately **not** remote config: an ad unit id is a store
operation, so `AdUnits.kt` is a compile-time table on purpose. Nothing about
this needs the server to be serving.

Do it in the release that ships and not before. A build that is not going to the
public tracks must not request a live unit; those impressions are invalid
traffic and that is what gets AdMob accounts suspended. Note that this includes
TestFlight and Play internal builds, which are *release* builds, so "is this a
debug build" is not the question to ask. See the note on deriving this from
`BuildInfo.releaseChannel` in `AdUnits.kt` if it has been written by the time
you read this.

### 9. QA the app · you, with an agent

- [ ] A full pass on a real iPhone and a real Android device, not a simulator.

The two things most worth doing deliberately, because both have failed here
before and neither shows up in a test: watch a rewarded ad through to the end
and then check the board still takes taps, and long-press the app icon to check
both quick actions launch from cold and from warm. `SD-129` in `todos.md` lists
the rest of what has moved since anyone last looked at it on hardware.

---

## Suggested

None of these block a release.

- [ ] **Grafana Cloud**, three secrets, for product analytics. Without them the
      OTLP tree is never planted and nothing looks broken, you simply have no
      analytics. Sentry is already live, so crashes are covered either way.
      Step 1's credential store holds these too.
- [ ] **Decide SD-131.** `DefaultNetworkConfig.baseUrl` is `""`, so no shipped
      build can read remote config and the admin console edits values no device
      will fetch. Either stand up Fly and Supabase, or write down that config is
      a compile-time table and the console is a staging tool. Both are fine.
      Believing the first while shipping the second is not.
- [ ] **Accessibility pass** (`OWNER-TODO.md` §19). Four known faults, three of
      them the same mistake in three components, which is why it wants one
      session rather than four tickets.
- [ ] **Android leaderboards** through Play Games (`OWNER-TODO.md` §16). Game
      Center is iOS only today and Android binds an inert seam.
- [ ] **Retake the Android screenshots.** The achievements page was rebuilt on
      2026-09-10 and the shot no longer matches the app.
- [ ] **The art in `OWNER-TODO.md` §8**, which blocks four punch-list items.
- [ ] **Remove `com.apple.developer.applesignin`** from `iosApp.entitlements`,
      left over from the deleted identity work.

---

## Already done

Listed so nobody redoes them. Both store records exist with listings, pricing,
categories and screenshots, including Play's phone, 7-inch and 10-inch sets.
Apple: App Privacy published, `PrivacyInfo.xcprivacy` written, `SKAdNetworkItems`
filled with 50 buyers, age rating 4+, content rights, `sodogku_pro` complete with
its review screenshot and notes, all three Game Center boards with images, and
the Paid Applications agreement signed. Play: IARC rating, Data safety, target
audience and every other content declaration. AdMob: both apps, four units, the
GDPR message. Sentry: live in CI and on this machine.
