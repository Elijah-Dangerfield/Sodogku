# Release checklist

What is left between here and Sodogku being downloadable. Two steps, then two
more only if you want them.

`OWNER-TODO.md` is the long form. This file is the order and the ownership.

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
(TestFlight and Play internal), `store` from `release.yml`.

Where the detail lives: `OWNER-TODO.md` for credentials and console work,
`todos.md` for the `SD-` queue, `docs/store/` for listing copy and the derived
privacy answers, `docs/reference/features.md` for what the app actually does.
Anything one of those asserts about a console is a claim as of a date, not a
live reading. Check the console before acting on it.

---

## The two steps

### 1. Merge the release-please PR

- [ ] Merge it. CI signs, builds and uploads to Play internal and TestFlight.

Every secret it needs is set: the upload keystore, the Play service account, the
App Store Connect key, the Apple distribution certificate and Sentry. Nothing
about this needs you beyond the merge.

**Two things happen the first time and never again.** Play routes this to the
internal track rather than production, because it will not accept a production
release until an approved one exists, and `release.yml` already detects that.
And because it is a `store` build, it carries live ad units in front of whoever
is on that internal track. Keep the list small and do not sit watching rewarded
ads on it.

**If the Play upload fails with a permissions error**, the service account is
shared across apps and may not have been granted access to this one yet. Play
Console → Users and permissions → the service account → add this app. Once,
ever.

### 2. Create `sodogku_pro` in Play, then QA, then submit

- [ ] Managed product, id `sodogku_pro`, $4.99. Only possible after step 1,
      because Play hides the in-app products page until a build carrying the
      billing permission exists. Without it Pro cannot be bought on Android.
      The App Store side is already complete.
- [ ] QA the build from TestFlight and from Play internal, on real hardware.
- [ ] Submit on both platforms.

On QA, two things are worth doing deliberately because both have failed here and
neither shows up in a test: **watch a rewarded ad to the end and check the board
still takes taps**, and **long-press the app icon** to check both quick actions
launch from cold and from warm. `SD-129` in `todos.md` lists what else has moved
since anyone last looked at the app on a device.

---

## Optional, and genuinely optional

Neither of these blocks a release, and the app is correct without them.

- [ ] **Grafana Cloud**, three secrets, for product analytics. Without them the
      OTLP tree is never planted and nothing looks broken; you simply have no
      analytics. Sentry is live, so crashes are covered either way. Run
      `./scripts/setup_credentials.main.kts` to store the values and
      `./scripts/setup_github_secrets.main.kts` to push them.
- [ ] **Move the legal pages to `nightjarlabs.llc`** and give every Nightjar app
      one **delete my data** form (SD-150). The `github.io` URLs work and are
      filed with both stores, so this is about not shipping a personal GitHub
      username, and about answering Play's deletion question with a real route
      instead of leaving it blank. It needs the studio site and the template
      changed as well, so it is not a Sodogku-only job.

---

## Already done

Listed so nobody redoes it.

**Signing and CI.** All eleven release secrets are set from the shared signing
folder, plus Sentry's two secrets and two variables. `scripts/` now carries the
template's setup scripts, so a new machine is `setup_credentials.main.kts` then
`setup_github_secrets.main.kts`.

**Both store records**, with listings, pricing, categories, screenshots
including Play's tablet sets, and a description that is 741 characters rather
than 2,450.

**Apple.** App Privacy published, `PrivacyInfo.xcprivacy` written and verified in
the built bundle, `SKAdNetworkItems` with 50 buyers, age rating 4+, content
rights, `sodogku_pro` complete with its review screenshot and notes, all three
Game Center boards with images, Paid Applications agreement signed, and the app
icon flattened so the upload cannot be rejected for an alpha channel.

**Play.** IARC rating, Data safety, target audience, and every other content
declaration. Listing icon flattened too.

**Ads.** Both AdMob apps and four units, the GDPR message, `SKAdNetworkItems`,
`MAX_AD_CONTENT_RATING_G` on both platforms, the under-16 flag ruled on, the
test-unit switch derived from the release channel so there is nothing to
remember, and SD-149's consent row in Settings.
