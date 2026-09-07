#!/usr/bin/env bash
# Installs the CI / release automation that was staged (but not enabled) when
# this project was generated from the template. The staged files under
# template/ci/ already went through the init-time rename and placeholder
# substitution, so installing them later is a pure file move.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -d template/ci ]; then
  echo "Nothing to install: template/ci/ not found (CI may already be enabled)." >&2
  exit 1
fi

cp -R template/ci/. .
rm -rf template
rm -- scripts/enable_ci.sh

echo "CI installed:"
echo "  - .github/workflows/ (ci, release-please, release, ...)"
echo "  - apps/ios fastlane files, pages/, release-please config"
echo
echo "Next: set the GitHub secrets listed in SETUP.md before the pipeline can ship."
