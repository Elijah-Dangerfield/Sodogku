#!/usr/bin/env bash
#
# Points this clone and this repo's CI at a Sentry project.
#
# Nothing in the app needs code changes to turn crash reporting on. The DSN
# resolves at build time (CI env SENTRY_DSN -> local.properties sentry.dsn ->
# blank) and a blank DSN leaves SentryRuntimeConfig.isEnabled false, so the
# whole pipe is credentials and nothing else. This script sets those
# credentials in the three places that read them:
#
#   local.properties          sentry.dsn, for builds off this machine
#   GitHub Actions secrets    SENTRY_DSN, SENTRY_AUTH_TOKEN
#   GitHub Actions variables  SENTRY_ORG, SENTRY_PROJECT  (variables, NOT
#                             secrets -- release.yml reads them via `vars.`
#                             and a secret of the same name reads as empty)
#
# Safe to re-run. It rewrites rather than appends, so running it twice does not
# leave two sentry.dsn lines in local.properties.
#
# Deliberately does NOT touch the server's SENTRY_DSN. That is a Fly secret on
# a separate deployment, and pointing the server at the mobile project mixes
# two very different event streams under one release string.
#
# Usage:
#   ./scripts/setup_sentry.sh
#
# It prompts for the auth token with terminal echo off, so the token never
# reaches your shell history.

set -euo pipefail

readonly DEFAULT_DSN="https://15b2b3fd0b0dbeb0b586b22e2c2821e3@o327796.ingest.us.sentry.io/4512058286931968"
readonly DEFAULT_ORG="elijah-dangerfield"
readonly DEFAULT_PROJECT="sodogku"

cd "$(dirname "$0")/.."

say() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

command -v gh >/dev/null 2>&1 || die "the GitHub CLI (gh) is not installed"
gh auth status >/dev/null 2>&1 || die "gh is not logged in -- run: gh auth login"
command -v curl >/dev/null 2>&1 || die "curl is not installed"

DSN="${SENTRY_DSN:-$DEFAULT_DSN}"
ORG="${SENTRY_ORG:-$DEFAULT_ORG}"
PROJECT="${SENTRY_PROJECT:-$DEFAULT_PROJECT}"

say "Sentry org:     $ORG"
say "Sentry project: $PROJECT"
say "DSN:            ${DSN%%@*}@..."
say ""
say "The auth token needs scopes: org:read project:read project:write project:releases"
say "Mint one at: https://$ORG.sentry.io/settings/auth-tokens/"
say ""

# `stty -echo` rather than `read -s`, which is not portable and means something
# else entirely in zsh. The trap puts echo back if the read is interrupted --
# without it, a ctrl-c here leaves the terminal silently swallowing keystrokes.
printf 'Paste the Sentry auth token (input hidden): '
stty -echo
trap 'stty echo' EXIT INT TERM
read -r TOKEN
stty echo
trap - EXIT INT TERM
printf '\n'

[ -n "$TOKEN" ] || die "no token entered; nothing was changed"

# Verify before writing anything anywhere. A token that is wrong, expired or
# under-scoped fails here rather than six weeks later as a silently skipped
# mapping upload in a release run -- which is the failure mode this whole
# pipeline is prone to, since every Sentry step in CI is guarded by
# `if: env.SENTRY_AUTH_TOKEN != ''` and passes loudly when it does nothing.
say ""
printf 'Checking the token against %s/%s ... ' "$ORG" "$PROJECT"
if ! curl -sf -H "Authorization: Bearer $TOKEN" \
    "https://sentry.io/api/0/projects/$ORG/$PROJECT/" -o /dev/null; then
    printf 'FAILED\n'
    die "the token could not read $ORG/$PROJECT -- check the slugs and the token's scopes"
fi
printf 'ok\n'

# local.properties: rewrite in place, keeping every other key.
say ""
printf 'Writing sentry.dsn to local.properties ... '
touch local.properties
grep -v '^sentry\.dsn=' local.properties > local.properties.tmp || true
printf 'sentry.dsn=%s\n' "$DSN" >> local.properties.tmp
mv local.properties.tmp local.properties
printf 'ok\n'

say ""
say "Setting GitHub Actions secrets and variables ..."
printf '%s' "$DSN"   | gh secret set SENTRY_DSN
printf '%s' "$TOKEN" | gh secret set SENTRY_AUTH_TOKEN
gh variable set SENTRY_ORG --body "$ORG"
gh variable set SENTRY_PROJECT --body "$PROJECT"

unset TOKEN

say ""
say "Done. What changed:"
say "  local.properties   sentry.dsn set (gitignored, this machine only)"
say "  repo secrets       SENTRY_DSN, SENTRY_AUTH_TOKEN"
say "  repo variables     SENTRY_ORG, SENTRY_PROJECT"
say ""
say "Next: rebuild so the DSN is baked in. Debug builds report too, tagged"
say "environment=<channel>-android-debug, so you can prove the loop today."
