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

### 1. ~~Merge the release-please PR~~ — done, 2026-09-25

`v0.3.0` is built, signed and uploaded. Both platforms are installable:

| | Where | What |
|---|---|---|
| Android | Play **internal** track | 0.3.0, version code 390 |
| iOS | TestFlight, internal group | build `202609252107`, ready for testing |

Both landed on a testing track rather than in front of the public, because both
platforms carry a first-release guard and this was the first release. Play will
not accept an automated production upload before an approved release exists, so
`release.yml` routed to `internal`. App Store Connect had zero builds, so the
same workflow resolved the iOS lane to `beta`: TestFlight only, **no App Store
submission**. Neither guard fires again.

It is still a `store` build, so it carries live ad units in front of whoever is
on that internal track. Keep the list small and do not sit watching rewarded ads
on it.

### 2. Create `sodogku_pro` in Play, then QA, then submit

- [ ] Managed product, id `sodogku_pro`, $4.99. Only possible now that a build
      carrying the billing permission exists, which is why Play hid the page
      until today. Without it Pro cannot be bought on Android. The App Store
      side is already complete.
- [ ] **Rename the App Store Connect version record from `1.0` to `0.3.0`.** It
      holds all the metadata and it is one field. A build whose version string
      is 0.3.0 cannot be attached to a record called 1.0, so the submission has
      nothing to attach to until these agree. TestFlight does not care, so this
      blocks submission and not QA.
- [ ] QA the build from TestFlight and from Play internal, on real hardware.
- [ ] Submit on both platforms, by hand this once: promote the Play internal
      release to production, and submit the iOS build against the renamed
      version record. From the next release on, merging the release PR does
      both.

On QA, two things are worth doing deliberately because both have failed here and
neither shows up in a test: **watch a rewarded ad to the end and check the board
still takes taps**, and **long-press the app icon** to check both quick actions
launch from cold and from warm. The first of those is not hypothetical: Sentry
caught it on a real phone on 2026-09-20 and two fixes went in on 2026-09-21, so
this run is the check on whether they worked. `OWNER-TODO.md` item 17 says what
the logs should and should not contain. `SD-129` in `todos.md` lists what else has moved
since anyone last looked at the app on a device.

---

## Optional, and genuinely optional

Neither of these blocks a release, and the app is correct without them.

- [ ] **Grafana Cloud**, three secrets, for product analytics. Without them the
      OTLP tree is never planted and nothing looks broken; you simply have no
      analytics. Sentry is live, so crashes are covered either way. Run
      `./scripts/setup_credentials.main.kts` to store the values and
      `./scripts/setup_github_secrets.main.kts` to push them.
- [ ] **Attach build artifacts to the GitHub Release.** The `attach-artifacts`
      job failed on `v0.3.0` with "Resource not accessible by integration". It
      asks for `contents: write`, but the repository caps the default workflow
      token at read, and a job cannot request more than the cap. Nothing about
      the release depends on it; you simply have no `.aab`, `.apk` or `.ipa`
      hanging off the tag. Fix it in Settings → Actions → General → Workflow
      permissions, or with
      `gh api -X PUT repos/Elijah-Dangerfield/Sodogku/actions/permissions/workflow -f default_workflow_permissions=write`.
      Worth knowing before flipping it: that grants write to every workflow in
      the repo, not just this job.

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

**Legal.** `privacy.md` and `terms.md` moved out of `pages/` into `legal/`, with
the sync workflow, the setup script and all eight compiled URL references
repointed. `pages/` and `pages.yml` are deleted. The studio site has a shared
delete-my-data form that emails the request. The pages are published and every
store URL was re-filed on 2026-09-25: Apple's support URL and privacy policy
URL, Play's privacy policy URL, and Play's Data safety delete-data URL. The old
`elijah-dangerfield.github.io/Sodogku/` still answers 200 because Pages keeps
serving its last deploy, and nothing points at it any more. Turn that Pages site
off once both stores have cleared review, or the frozen copy outlives the real
one.

**Export compliance.** `ITSAppUsesNonExemptEncryption=false` is in
`Info.plist`. Before it was, build `202609252107` finished processing and then
sat in TestFlight as "Missing Compliance", installable by nobody. The answer for
that build was filed through the API on 2026-09-25; the key means no later build
stalls the same way. The app's only encryption is system TLS, which Apple
exempts.

**Ads.** Play's listing website repointed at `nightjarlabs.llc`, the bare domain,
so the `app-ads.txt` there is finally where AdMob's crawler looks for it. Both
AdMob apps and four units, the GDPR message, `SKAdNetworkItems`,
`MAX_AD_CONTENT_RATING_G` on both platforms, the under-16 flag ruled on, the
test-unit switch derived from the release channel so there is nothing to
remember, and SD-149's consent row in Settings.
