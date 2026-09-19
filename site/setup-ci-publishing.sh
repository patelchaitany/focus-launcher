#!/bin/bash
# Switches on publishing from GitHub Actions (.github/workflows/publish.yml).
#
#   site/setup-ci-publishing.sh          run ONCE, by the owner, on the machine that has the
#                                        release key (keystore.properties) and server access
#                                        (site/deploy.env)
#   site/setup-ci-publishing.sh --off    switch it off again and remove what this script added
#
# It is a script the owner runs himself because everything in it is about keys:
#   1. creates the GitHub environment "release": only the owner can approve a run that uses it,
#      and only runs from main may ask;
#   2. stores the signing key and its passwords as secrets OF THAT ENVIRONMENT (GitHub encrypts
#      them on this machine before upload; nobody can read them back, approved runs can use them);
#   3. makes a new upload key for the server that can do exactly one thing: hand a site archive to
#      site/server/receive.sh. It cannot open a shell. Your own login key is NOT given to GitHub;
#   4. sets the repository variable FOCUS_PUBLISHING=on, which the workflow checks.
# Nothing secret is printed, and the private half of the upload key is deleted from this machine
# as soon as GitHub has it.
set -euo pipefail
cd "$(dirname "$0")/.."

ENVIRONMENT=release
MARK=focus-ci-deploy                 # the comment that identifies our line in authorized_keys
say()  { printf '\n== %s\n' "$*"; }
fail() { printf 'STOP: %s\n' "$*" >&2; exit 1; }

for tool in gh ssh ssh-keygen base64 sed awk; do command -v "$tool" >/dev/null || fail "$tool is not installed"; done
gh auth status >/dev/null 2>&1 || fail "gh is not logged in (gh auth login)"
REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
[ "$(gh api "repos/$REPO" --jq .permissions.admin)" = "true" ] || fail "you need admin rights on $REPO"
[ -f site/deploy.env ] || fail "site/deploy.env is missing (copy site/deploy.env.example)"
# shellcheck disable=SC1091
. site/deploy.env
: "${FOCUS_DEPLOY_HOST:?set FOCUS_DEPLOY_HOST in site/deploy.env}"
: "${FOCUS_DEPLOY_KEY:?set FOCUS_DEPLOY_KEY in site/deploy.env}"
SSH=(ssh -i "$FOCUS_DEPLOY_KEY" -o BatchMode=yes -o ConnectTimeout=20 "$FOCUS_DEPLOY_HOST")

remove_server_side() {
  "${SSH[@]}" "MARK=$MARK bash -s" <<'REMOTE'
set -e; umask 077
if [ -f ~/.ssh/authorized_keys ] && grep -q " $MARK\$" ~/.ssh/authorized_keys; then
  cp -p ~/.ssh/authorized_keys ~/.ssh/authorized_keys.bak-$(date +%Y%m%d-%H%M%S)
  grep -v " $MARK\$" ~/.ssh/authorized_keys > ~/.ssh/authorized_keys.new || true
  chmod 600 ~/.ssh/authorized_keys.new && mv ~/.ssh/authorized_keys.new ~/.ssh/authorized_keys
  echo "upload key removed from the server"
else
  echo "no upload key on the server"
fi
rm -rf ~/focus-deploy
REMOTE
}

if [ "${1:-}" = "--off" ]; then
  say "Switching CI publishing off for $REPO"
  gh variable delete FOCUS_PUBLISHING --repo "$REPO" 2>/dev/null || true
  for s in FOCUS_KEYSTORE_B64 FOCUS_STORE_PASSWORD FOCUS_KEY_ALIAS FOCUS_KEY_PASSWORD FOCUS_DEPLOY_SSH_KEY FOCUS_DEPLOY_TARGET FOCUS_DEPLOY_KNOWN_HOSTS; do
    gh secret delete "$s" --env "$ENVIRONMENT" --repo "$REPO" 2>/dev/null || true
  done
  remove_server_side
  echo "Off. The Publish workflow skips itself again; the environment \"$ENVIRONMENT\" was left in place, empty."
  exit 0
fi

[ -f keystore.properties ] || fail "keystore.properties is missing: this machine does not have the release key"
prop() { sed -n "s/^$1=//p" keystore.properties | head -1; }
STORE_FILE=$(prop storeFile); STORE_FILE=${STORE_FILE/#\~/$HOME}
[ -f "$STORE_FILE" ] || fail "the keystore named in keystore.properties does not exist"
for k in storePassword keyAlias keyPassword; do [ -n "$(prop $k)" ] || fail "keystore.properties has no $k"; done

cat <<EOF

This will let GitHub Actions publish Focus for $REPO:
  - your release signing key and its passwords become secrets of the GitHub environment
    "$ENVIRONMENT"; only runs that YOU approve can use them;
  - a new, restricted upload key is installed on your server (it can only deliver site files);
  - your own server login key stays on this machine.
Undo at any time with:  site/setup-ci-publishing.sh --off
EOF
if [ "${1:-}" != "--yes" ]; then
  printf '\nContinue? Type yes: '
  read -r answer </dev/tty || fail "no terminal to ask on; re-run with --yes if you mean it"
  [ "$answer" = "yes" ] || fail "nothing was changed"
fi

say "1/5  Environment \"$ENVIRONMENT\": you as the required reviewer, main only"
USER_ID=$(gh api user --jq .id)
gh api -X PUT "repos/$REPO/environments/$ENVIRONMENT" --silent --input - <<EOF
{"wait_timer":0,"prevent_self_review":false,"reviewers":[{"type":"User","id":$USER_ID}],
 "deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
EOF
gh api "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" --jq '.branch_policies[].name' | grep -qx main \
  || gh api -X POST "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" --silent -f name=main -f type=branch
reviewers=$(gh api "repos/$REPO/environments/$ENVIRONMENT" --jq '[.protection_rules[] | select(.type=="required_reviewers") | .reviewers[].reviewer.login] | join(",")')
[ -n "$reviewers" ] || fail "GitHub did not accept the required reviewer: secrets were NOT uploaded"
echo "required reviewer: $reviewers"

say "2/5  Signing key -> environment secrets (values are never shown)"
base64 < "$STORE_FILE" | tr -d '\n' | gh secret set FOCUS_KEYSTORE_B64 --env "$ENVIRONMENT" --repo "$REPO"
printf '%s' "$(prop storePassword)" | gh secret set FOCUS_STORE_PASSWORD --env "$ENVIRONMENT" --repo "$REPO"
printf '%s' "$(prop keyAlias)"      | gh secret set FOCUS_KEY_ALIAS      --env "$ENVIRONMENT" --repo "$REPO"
printf '%s' "$(prop keyPassword)"   | gh secret set FOCUS_KEY_PASSWORD   --env "$ENVIRONMENT" --repo "$REPO"

say "3/5  A restricted upload key, and its receiver, on the server"
TMP=$(mktemp -d); chmod 700 "$TMP"; trap 'rm -rf "$TMP"' EXIT
ssh-keygen -q -t ed25519 -N '' -C "$MARK" -f "$TMP/key"
"${SSH[@]}" 'mkdir -p ~/focus-deploy && cat > ~/focus-deploy/receive.sh.new && chmod 755 ~/focus-deploy/receive.sh.new && mv ~/focus-deploy/receive.sh.new ~/focus-deploy/receive.sh' < site/server/receive.sh
# Our line is appended to a COPY of authorized_keys; the copy replaces the original only if every
# other line is still in it, byte for byte. A dated backup is kept next to it.
PUB=$(cat "$TMP/key.pub")
"${SSH[@]}" "MARK=$MARK PUB='$PUB' bash -s" <<'REMOTE'
set -e; umask 077
mkdir -p ~/.ssh; touch ~/.ssh/authorized_keys
cp -p ~/.ssh/authorized_keys ~/.ssh/authorized_keys.bak-$(date +%Y%m%d-%H%M%S)
grep -v " $MARK\$" ~/.ssh/authorized_keys > ~/.ssh/authorized_keys.new || true
before=$(grep -vc " $MARK\$" ~/.ssh/authorized_keys || true)
printf 'restrict,command="%s/focus-deploy/receive.sh" %s\n' "$HOME" "$PUB" >> ~/.ssh/authorized_keys.new
after=$(grep -vc " $MARK\$" ~/.ssh/authorized_keys.new || true)
[ "$before" = "$after" ] || { echo "authorized_keys would lose a line: nothing replaced" >&2; rm -f ~/.ssh/authorized_keys.new; exit 1; }
chmod 600 ~/.ssh/authorized_keys.new; mv ~/.ssh/authorized_keys.new ~/.ssh/authorized_keys
echo "upload key installed ($(grep -c . ~/.ssh/authorized_keys) lines in authorized_keys, $before of them yours)"
REMOTE
"${SSH[@]}" true || fail "your own login stopped working: restore ~/.ssh/authorized_keys.bak-* from the server console"

say "4/5  Proving the new key is locked down"
HOST_ONLY=${FOCUS_DEPLOY_HOST#*@}
KNOWN=$(ssh-keygen -F "$HOST_ONLY" 2>/dev/null | grep -v '^#' | awk -v h="$HOST_ONLY" '{print h" "$2" "$3}')
[ -n "$KNOWN" ] || fail "the server's host key is not in ~/.ssh/known_hosts"
printf '%s\n' "$KNOWN" > "$TMP/known_hosts"
CI_SSH=(ssh -i "$TMP/key" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$TMP/known_hosts" -o LogLevel=ERROR -o ConnectTimeout=20 "$FOCUS_DEPLOY_HOST")
reply=$("${CI_SSH[@]}" 'echo A-SHELL-WAS-GIVEN; id' </dev/null 2>&1 || true)
case "$reply" in
  *A-SHELL-WAS-GIVEN*) fail "the new key can run commands: the forced command is not in effect" ;;
  *"refused: empty upload"*) echo "ok: the key reaches the receiver and nothing else" ;;
  *) fail "unexpected answer from the server with the new key: $(printf '%s' "$reply" | head -1 | cut -c1-80)" ;;
esac

say "5/5  Upload key -> environment secrets, then the switch"
gh secret set FOCUS_DEPLOY_SSH_KEY --env "$ENVIRONMENT" --repo "$REPO" < "$TMP/key"
printf '%s' "$FOCUS_DEPLOY_HOST" | gh secret set FOCUS_DEPLOY_TARGET      --env "$ENVIRONMENT" --repo "$REPO"
printf '%s' "$KNOWN"             | gh secret set FOCUS_DEPLOY_KNOWN_HOSTS --env "$ENVIRONMENT" --repo "$REPO"
rm -rf "$TMP"
gh variable set FOCUS_PUBLISHING --body on --repo "$REPO"

cat <<EOF

Done. Secrets in the "$ENVIRONMENT" environment:
$(gh secret list --env "$ENVIRONMENT" --repo "$REPO" | awk '{print "  " $1}')

From now on a push to main that changes the app or the site starts "Publish", which waits for
you:  https://github.com/$REPO/actions/workflows/publish.yml  ->  the waiting run  ->
"Review deployments"  ->  Approve.  Start one by hand with:  gh workflow run publish.yml
EOF
