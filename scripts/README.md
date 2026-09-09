# Scripts

Utility scripts for this project.

> Template maintainers: init/verification tooling (`init_project.main.kts`,
> `verify_template.sh`) is documented in `docs/template-maintenance.md`.
> Those scripts are removed from generated projects.

## setup_sentry.sh

Turns crash reporting on. Prompts for a Sentry auth token (echo off), verifies
it against the project before writing anything, then sets `sentry.dsn` in
`local.properties`, the `SENTRY_DSN` / `SENTRY_AUTH_TOKEN` repo secrets, and
the `SENTRY_ORG` / `SENTRY_PROJECT` repo **variables**:

```bash
./scripts/setup_sentry.sh
```

Safe to re-run. Does not touch the server's own `SENTRY_DSN`, which is a Fly
secret on a separate deployment. See SETUP.md for where the token comes from.

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

## rotate_apple_sign_in_token.main.kts

Rotates the Apple Sign In client secret (it expires at most every 6 months).

## cleanup.sh

Cleans build artifacts and caches:

```bash
./scripts/cleanup.sh
```
