#!/usr/bin/env bash
# Generated-project smoke test: initializes a fresh project from this template
# (non-interactively, CI enabled) into a temp dir, then asserts the result is
# clean and builds. This is the gate that stops the template from shipping a
# broken generator — run it before any commit that adds a module, touches the
# init script, or changes staged CI.
#
# Usage:
#   scripts/verify_template.sh --fast   # static checks + Android compile + unit tests (default)
#   scripts/verify_template.sh --full   # adds iOS Kotlin compile, server tests, and
#                                       # (once present) admin JS bundle + server docker build
#
# Env:
#   VERIFY_INCLUDE_SERVER_TESTS=1   # run :apps:server:test even in --fast mode
#                                   # (used by template CI, where docker is free)
#   VERIFY_KEEP=1                   # keep the generated project dir for inspection
#
# This file is deleted from generated projects by the init script.
set -euo pipefail

MODE="${1:---fast}"
case "$MODE" in
  --fast|--full) ;;
  *) echo "usage: $0 [--fast|--full]" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
PROJECT_DIR="$WORK/SmokeTestApp"
if [ "${VERIFY_KEEP:-0}" = "1" ]; then
  trap 'echo "VERIFY_KEEP=1 — generated project left at $PROJECT_DIR"' EXIT
else
  trap 'rm -rf "$WORK"' EXIT
fi

if ! command -v kotlin >/dev/null 2>&1; then
  echo "error: the kotlin CLI is required to run the init script (brew install kotlin)" >&2
  exit 1
fi

echo "==> Initializing project into $PROJECT_DIR (non-interactive, CI enabled)"
(
  cd "$ROOT"
  ./scripts/init_project.main.kts \
    --name "Smoke Test App" \
    --package com.smoketest.app \
    --email smoke@example.com \
    --dir "$PROJECT_DIR" \
    --ci=yes --yes
)

cd "$PROJECT_DIR"
FAILURES=0
fail() { echo "FAIL: $*" >&2; FAILURES=$((FAILURES + 1)); }

echo "==> Static assertions"

# 1. No residual template naming in any form (KMPTemplate, kmptemplate,
#    com.kmptemplate, kmp-template, kmp_template, kmp.template, "KMP Template").
if grep -rIiE --exclude-dir=.git 'kmp[ ._-]?template' . >/tmp/verify_residual.txt 2>/dev/null; then
  head -20 /tmp/verify_residual.txt >&2
  fail "residual template naming found (see above)"
fi

# 2. No Cards branding leaked in from backported code. 'cards' needs word
#    boundaries (Wildcards, Discards, CardSecondary all contain it);
#    downcard/warm-felt are distinctive enough to match anywhere. The two
#    history docs legitimately discuss the Cards app as the origin of
#    backported systems. BSD grep strips the leading ./ that GNU grep emits,
#    so the allowlist has to anchor on both or it silently matches nothing
#    when this runs on a Mac.
BRAND_ALLOWLIST='^(\./)?docs/(decisions|backend-and-supabase-auth-plan)\.md:'
if grep -rIiE --exclude-dir=.git '(^|[^A-Za-z])cards([^A-Za-z]|$)|downcard|warm-felt' . 2>/dev/null \
    | grep -vE "$BRAND_ALLOWLIST" >/tmp/verify_brand.txt; then
  head -20 /tmp/verify_brand.txt >&2
  fail "Cards branding found (see above)"
fi

# 3. No unsubstituted {{PLACEHOLDER}} tokens. GitHub Actions ${{ ... }}
#    expressions don't match: contexts are lowercase (secrets./env./inputs.).
if grep -rIE --exclude-dir=.git '\{\{[A-Z_]+\}\}' . >/tmp/verify_placeholders.txt 2>/dev/null; then
  head -20 /tmp/verify_placeholders.txt >&2
  fail "unsubstituted placeholders found (see above)"
fi

# 4. Executable bits survived the copy.
for f in gradlew scripts/install_hooks.sh .githooks/commit-msg .githooks/post-commit; do
  [ -x "$f" ] || fail "$f is not executable"
done
if [ -f .githooks/pre-push ] && [ ! -x .githooks/pre-push ]; then
  fail ".githooks/pre-push is not executable"
fi

# 5. Template-only artifacts were deleted.
for f in \
  scripts/init_project.main.kts \
  scripts/rename_to_template.sh \
  scripts/verify_template.sh \
  scripts/enable_ci.sh \
  docs/template-maintenance.md \
  docs/cards-backport-plan.md \
  docs/template-upgrade-execution-plan.md \
  docs/PORT-CANDIDATES.md \
  docs/plans; do
  [ ! -e "$f" ] || fail "template artifact shipped into generated project: $f"
done
[ ! -d template ] || fail "template/ staging dir shipped despite CI being enabled"
[ -d .github/workflows ] || fail "CI was requested but .github/workflows is missing"

# 6. Hook installation works out of the box.
./scripts/install_hooks.sh >/dev/null || fail "scripts/install_hooks.sh failed"

# 7. Exactly one commit of fresh history.
COMMITS="$(git rev-list --count HEAD 2>/dev/null || echo 0)"
[ "$COMMITS" = "1" ] || fail "expected exactly 1 git commit, found $COMMITS"

# 8. Every release-please `extra-files` entry exists and carries a version
#    marker. release-please skips an unmarked extra-file SILENTLY — no warning,
#    no error, exit 0 — so nothing else in this pipeline can catch it. The
#    symptom is not a failed release; it is a green one that tags and changelogs
#    a new version while the version files stay behind, shipping the new code
#    labelled with the old version to both stores. Asserted here because a
#    deleted marker looks like a tidy-up in review.
if [ -f release-please-config.json ]; then
  python3 - <<'PYEOF' || fail "release-please extra-files are missing version markers (see above)"
import json, os, sys
cfg = json.load(open("release-please-config.json"))
bad = []
for pkg in cfg.get("packages", {}).values():
    for entry in pkg.get("extra-files", []):
        path = entry if isinstance(entry, str) else entry.get("path", "")
        if not path:
            continue
        if not os.path.exists(path):
            bad.append(f"{path}: listed in extra-files but does not exist")
        elif "x-release-please-start-version" not in open(path, encoding="utf-8").read():
            bad.append(f"{path}: no x-release-please-start-version marker")
for line in bad:
    print(f"  {line}", file=sys.stderr)
sys.exit(1 if bad else 0)
PYEOF
fi

if [ "$FAILURES" -gt 0 ]; then
  echo "==> $FAILURES static assertion(s) failed — aborting before build" >&2
  exit 1
fi

echo "==> Static assertions passed"

if command -v actionlint >/dev/null 2>&1; then
  echo "==> actionlint on generated workflows"
  actionlint .github/workflows/*.yml
else
  echo "==> actionlint not installed — skipping workflow lint (brew install actionlint)"
fi

echo "==> Building generated project ($MODE)"
# Generated projects don't ship local.properties. Locally (no ANDROID_HOME)
# borrow the template's so the Android plugin can find the SDK; CI runners
# have ANDROID_HOME set and skip this.
if [ -z "${ANDROID_HOME:-}" ] && [ -f "$ROOT/local.properties" ]; then
  cp "$ROOT/local.properties" local.properties
fi
./gradlew --no-daemon :apps:compose:compileDebugKotlinAndroid testDebugUnitTest

run_server_tests() {
  if docker info >/dev/null 2>&1; then
    ./gradlew --no-daemon :apps:server:test
  else
    echo "==> docker daemon not reachable — skipping :apps:server:test (start Docker to include it)" >&2
    if [ "${CI:-}" = "true" ]; then
      echo "error: server tests are mandatory in CI" >&2
      exit 1
    fi
  fi
}

if [ "${VERIFY_INCLUDE_SERVER_TESTS:-0}" = "1" ] && [ "$MODE" = "--fast" ]; then
  run_server_tests
fi

if [ "$MODE" = "--full" ]; then
  ./gradlew --no-daemon :apps:compose:compileKotlinIosSimulatorArm64
  run_server_tests
  if grep -q 'apps:admin' settings.gradle.kts; then
    ./gradlew --no-daemon :apps:admin:jsBrowserDistribution
  fi
  if docker info >/dev/null 2>&1; then
    docker build -t template-verify-server -f apps/server/Dockerfile .
    docker rmi template-verify-server >/dev/null 2>&1 || true
  else
    echo "==> docker daemon not reachable — skipping server image build" >&2
  fi
fi

echo "==> verify_template.sh $MODE: PASS"
