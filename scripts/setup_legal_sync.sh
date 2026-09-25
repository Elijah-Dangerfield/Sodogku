#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Sets up the two secrets the Legal Sync pipeline needs.
#
#   1. NIGHTJAR_SITE_TOKEN on this repo, so this repo can open a pull request
#      against the website repo. GitHub has no API for minting a personal access
#      token, so this one you create in the browser and paste in. Per app.
#   2. FIREBASE_SERVICE_ACCOUNT on the website repo, so merging that PR deploys
#      the site. Once ever, across all apps.
#
# Order matters. The per-app token comes first because it is the part you are
# most likely to be re-running for, and the Firebase step is skipped outright
# when the secret already exists. An earlier version did Firebase first and
# unconditionally, which meant re-running this to fix a bad token stopped on a
# gcloud error for work that was already done.
#
# Safe to re-run.
#
# Needs: gh (logged in). gcloud (logged in as a project owner) only if the
# Firebase secret still has to be created, which is why that check lives inside
# that branch rather than up here.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SITE_REPO="Elijah-Dangerfield/nightjar"
GCP_PROJECT="nightjarlabs-db51f"
SA_NAME="github-action-legal-sync"
SA_EMAIL="${SA_NAME}@${GCP_PROJECT}.iam.gserviceaccount.com"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
step() { printf "\n\033[1;36m==> %s\033[0m\n" "$1"; }
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }

# ── Preflight ────────────────────────────────────────────────────────────────
step "Checking tools"
command -v gh >/dev/null 2>&1 || {
  echo "  ✗ gh is not installed. brew install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || {
  echo "  ✗ gh is not logged in. Run: gh auth login" >&2; exit 1; }
ok "gh is logged in"

APP_REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)" || {
  echo "  ✗ No GitHub remote here. Push this repo first." >&2; exit 1; }
ok "this repo is $APP_REPO"

# ── 1. Cross-repo pull request token ─────────────────────────────────────────
step "Pull request token for $APP_REPO"

replace=yes
if gh secret list --repo "$APP_REPO" | grep -q '^NIGHTJAR_SITE_TOKEN'; then
  bold "  NIGHTJAR_SITE_TOKEN already exists on $APP_REPO."
  echo "  Replace it if Legal Sync is failing. A 404 on the website repo means"
  echo "  the token authenticates but cannot see it, which is a scope problem,"
  echo "  not a bad paste."
  printf "  Replace it? (y/N): "
  read -r answer
  case "$answer" in [yY]*) ;; *) ok "kept the existing token"; replace=no ;; esac
fi

if [ "$replace" != "no" ]; then
  cat <<EOF

  Create a fine-grained personal access token:

    https://github.com/settings/personal-access-tokens/new

    Resource owner    the account that owns $SITE_REPO
    Repository access Only select repositories, then pick ${SITE_REPO#*/}
                      NOT this repo. The token is used to reach the website
                      repo; a token scoped to this one gets a 404 that looks
                      like the repo does not exist.
    Permissions       Contents      → Read and write
                      Pull requests → Read and write
    Expiration        Your call. When it expires, Legal Sync fails loudly on
                      the next push to legal/. It does not fail silently.

  Nothing else. A token with more access than this buys you nothing.

EOF
  stty -echo
  printf "  Paste the token (input hidden): "
  read -r TOKEN
  stty echo
  printf "\n"

  if [ -z "$TOKEN" ]; then
    echo "  ✗ No token entered. Nothing was set." >&2
    exit 1
  fi
  if [ "${TOKEN#github_pat_}" = "$TOKEN" ]; then
    echo "  ✗ That does not look like a fine-grained token (expected a github_pat_ prefix)." >&2
    echo "    A classic 'ghp_' token works too, but grants far more than this needs." >&2
    exit 1
  fi

  # Check the token can actually see the website repo before storing it. This
  # is the exact call actions/checkout makes, and its failure is the confusing
  # one: GitHub answers 404 rather than 403 for a repo the token cannot see, so
  # a wrongly-scoped token looks like a missing repo.
  if ! GH_TOKEN="$TOKEN" gh api "repos/$SITE_REPO" >/dev/null 2>&1; then
    echo "  ✗ That token cannot see $SITE_REPO." >&2
    echo "    Either its Repository access does not include ${SITE_REPO#*/}, or it is a" >&2
    echo "    fine-grained token still awaiting approval. Nothing was set." >&2
    unset TOKEN
    exit 1
  fi
  ok "token can read $SITE_REPO"

  printf '%s' "$TOKEN" | gh secret set NIGHTJAR_SITE_TOKEN --repo "$APP_REPO"
  unset TOKEN
  ok "set NIGHTJAR_SITE_TOKEN on $APP_REPO"
fi

# ── 2. Firebase deploy credentials, once across all apps ─────────────────────
step "Firebase service account for $SITE_REPO"

if gh secret list --repo "$SITE_REPO" 2>/dev/null | grep -q '^FIREBASE_SERVICE_ACCOUNT'; then
  ok "FIREBASE_SERVICE_ACCOUNT already set, nothing to do"
else
  command -v gcloud >/dev/null 2>&1 || {
    echo "  ✗ gcloud is not installed and the Firebase secret is missing." >&2
    echo "    brew install --cask google-cloud-sdk, then re-run." >&2; exit 1; }

  # `gcloud auth list` reports an account as ACTIVE even when its refresh token
  # has been revoked or expired, so it is not a credential check. Minting an
  # access token exercises the refresh path, which is what every call below
  # actually needs.
  gcloud auth print-access-token >/dev/null 2>&1 || {
    echo "  ✗ gcloud credentials will not refresh. Run: gcloud auth login" >&2
    echo "    (an account can look ACTIVE in 'gcloud auth list' and still be stale)" >&2
    exit 1; }
  ok "gcloud is logged in as $(gcloud auth list --filter=status:ACTIVE --format='value(account)' | head -1)"

  gcloud services enable \
    firebasehosting.googleapis.com iam.googleapis.com iamcredentials.googleapis.com \
    --project "$GCP_PROJECT" --quiet
  ok "APIs enabled"

  if gcloud iam service-accounts describe "$SA_EMAIL" --project "$GCP_PROJECT" >/dev/null 2>&1; then
    ok "service account already exists: $SA_EMAIL"
  else
    gcloud iam service-accounts create "$SA_NAME" \
      --project "$GCP_PROJECT" \
      --display-name "GitHub Actions: deploy the studio site" --quiet
    ok "created $SA_EMAIL"
  fi

  # firebasehosting.admin publishes releases; firebase.viewer lets the action
  # resolve the site id. The same two roles `firebase init hosting:github`
  # grants.
  for role in roles/firebasehosting.admin roles/firebase.viewer; do
    gcloud projects add-iam-policy-binding "$GCP_PROJECT" \
      --member "serviceAccount:${SA_EMAIL}" --role "$role" \
      --condition=None --quiet >/dev/null
    ok "granted $role"
  done

  # The key never touches the repo and never lands in a world-readable temp file.
  KEY_FILE="$(umask 077 && mktemp -t fb-sa-key)"
  trap 'rm -f "$KEY_FILE"' EXIT
  gcloud iam service-accounts keys create "$KEY_FILE" \
    --iam-account "$SA_EMAIL" --project "$GCP_PROJECT" --quiet
  gh secret set FIREBASE_SERVICE_ACCOUNT --repo "$SITE_REPO" < "$KEY_FILE"
  rm -f "$KEY_FILE"
  trap - EXIT
  ok "set FIREBASE_SERVICE_ACCOUNT on $SITE_REPO"
fi

# ── Done ─────────────────────────────────────────────────────────────────────
cat <<EOF

$(bold "Done.")

Try it end to end:

  gh workflow run legal-sync.yml --repo $APP_REPO

That opens a PR on $SITE_REPO. Read the rendered preview it comments,
then merge to publish.

One more thing, and an agent cannot do it for you: add this app to
src/data/apps.ts in $SITE_REPO with a matching legalSlug,
so the site links to the pages it now hosts.

EOF
