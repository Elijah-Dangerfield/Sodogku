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
say "Mint an *organization* token at:"
say "  https://$ORG.sentry.io/settings/auth-tokens/"
say "Its scopes are fixed (org:ci -- source map upload, release creation, code"
say "mappings) and that is exactly what CI does. A legacy user auth token with"
say "project:releases and org:read also works."
say ""

# Refuse rather than fall back to a visible prompt. Somewhere that cannot hide
# the input is somewhere the token ends up in a scrollback buffer.
[ -t 0 ] || die "this needs an interactive terminal to read the token without echoing it"

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
#
# Probe the capability CI actually uses, not a convenient endpoint. The first
# version of this asked for the *project* and rejected a perfectly good token:
# organization tokens carry a fixed org:ci scope set that covers uploads and
# releases and deliberately excludes project:read, so the probe failed on the
# one kind of token Sentry's own UI steers you toward. chunk-upload is what
# `sentry-cli upload-proguard` and `debug-files upload` go through, so a token
# that can reach it can do the job by definition.
#
# The project endpoint stays as a fallback for a legacy user auth token, which
# can read projects but may not advertise chunk-upload.
status_of() {
    curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$1"
}

say ""
printf 'Checking the token ... '
CHUNK_STATUS=$(status_of "https://sentry.io/api/0/organizations/$ORG/chunk-upload/")
if [ "$CHUNK_STATUS" = "200" ]; then
    printf 'ok (organization token)\n'
else
    PROJECT_STATUS=$(status_of "https://sentry.io/api/0/projects/$ORG/$PROJECT/")
    if [ "$PROJECT_STATUS" = "200" ]; then
        printf 'ok (user auth token)\n'
    else
        printf 'FAILED\n'
        say "  chunk-upload  -> HTTP $CHUNK_STATUS"
        say "  project       -> HTTP $PROJECT_STATUS"
        case "$CHUNK_STATUS" in
            401) die "the token was rejected -- it is mistyped, revoked or from another org" ;;
            403) die "the token is valid but under-scoped for uploads" ;;
            404) die "no org '$ORG' -- check the slug at https://sentry.io/settings/" ;;
            *)   die "could not verify the token (see the two statuses above)" ;;
        esac
    fi
fi

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
