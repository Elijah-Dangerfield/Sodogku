# Scripts

Utility scripts for this project.

> Template maintainers: init/verification tooling (`init_project.main.kts`,
> `verify_template.sh`) is documented in `docs/template-maintenance.md`.
> Those scripts are removed from generated projects.

## Setup scripts, ported from KMPTemplate

These read one machine-local credential store plus one folder of signing
material that lives outside every repo, so the values that belong to your Apple
team, your Play account and your Sentry org are entered once and every app
reuses them.

```bash
./scripts/setup_credentials.main.kts --list   # what is stored, masked
./scripts/setup_credentials.main.kts          # fill in anything missing
./scripts/setup_github_secrets.main.kts       # push every release secret to this repo
./scripts/setup_sentry.main.kts               # Sentry DSN, secrets and variables
./scripts/setup_fly.main.kts                  # Fly apps and their secrets
./scripts/setup_supabase.main.kts             # a Postgres per environment
```

`setup_github_secrets.main.kts --dry-run` prints what it would push and why any
value is missing, without touching the repo. Nothing is required; anything it
cannot find is reported with what that costs.

This replaced `setup_sentry.sh`, which did one of these jobs with none of the
shared store behind it.

## beta_ios.sh

Builds the iOS app and uploads it to TestFlight internal from this machine,
which is the same lane `beta.yml` runs:

```bash
./scripts/beta_ios.sh
```

Prefer it to dispatching the workflow. The hosted macOS runner starts cold
every time and spends over ten minutes on the Kotlin/Native release link alone
before it reaches the archive. Reach for `gh workflow run beta.yml` when the
thing being tested is CI itself: the runner image, the secrets, the workflow.

It reads `APPLE_TEAM_ID`, the three App Store Connect key values and the Sentry
token from the same credential store the setup scripts write, encodes the `.p8`
the way a CI secret would carry it, and takes the Sentry org and project from
the repo's own variables. Anything already in your environment wins. The build
is tagged `beta`, as the workflow tags it, so it requests test ad units rather
than live ones.

One-time setup: `(cd apps/ios && bundle install)`. Signing uses your own
keychain rather than the stored `.p12`, which is what the Fastfile intends for
a local run.

## install_hooks.sh

Installs the repo's git hooks (`.githooks/`) into your local clone. Run once
after cloning, before your first commit:

```bash
./scripts/install_hooks.sh
```

## enable_ci.sh

Present only if CI was declined at project init. Installs the staged CI /
release automation (`.github/workflows/`, fastlane files, `pages/`,
release-please config) and then removes itself:

```bash
./scripts/enable_ci.sh
```

See SETUP.md for the GitHub secrets the pipeline needs.

## create_module.main.kts

Creates new KMP modules with proper structure and configuration.

- KMP source sets (`commonMain`, `androidMain`, `iosMain`) and the right
  convention plugin per module type
- Feature modules get a Screen + ViewModel starter; libraries get a basic
  class; the public/impl split is supported for libraries
- Updates `settings.gradle.kts` and `apps/compose/build.gradle.kts`

```bash
./scripts/create_module.main.kts                      # interactive
./scripts/create_module.main.kts feature messaging    # feature module
./scripts/create_module.main.kts library analytics    # library module
./scripts/create_module.main.kts library user:preferences  # sub-module
```

## cleanup.sh

Cleans build artifacts and caches:

```bash
./scripts/cleanup.sh
```

## build_leaderboard_art.py

Builds the three Game Center leaderboard images into
`docs/store/gamecenter/`, at 1024x1024, RGB, no alpha, which is what App Store
Connect takes. They are assembled from art the project already owns rather than
drawn: the app icon's gradient, tilted grid and paws, with a dog still or the
streak flame on top.

```bash
./scripts/build_leaderboard_art.py            # rebuild all three
./scripts/build_leaderboard_art.py --check    # exit 1 if any is out of date
```

Placeholder in the same sense as the achievement emoji in `AchievementCopy`. If
real badge art ever lands, point the script at it instead of editing the PNGs.
