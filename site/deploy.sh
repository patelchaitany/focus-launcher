#!/bin/bash
# Builds the site and uploads it to https://how2me.me/focusapp/
#
#   ./gradlew :app:assembleDist && site/deploy.sh
#
# Only files in /var/www/focusapp are touched. nginx is not: its rules were installed once
# (site/nginx-focusapp.conf -> /etc/nginx/snippets/focusapp.conf, included from the how2me site).
# Old APK versions are left on the server, so links to them keep working.
set -euo pipefail
cd "$(dirname "$0")"
# Where to deploy is machine-specific and stays out of version control: copy deploy.env.example
# to deploy.env and fill it in (or export the variables yourself).
[ -f deploy.env ] && . ./deploy.env
: "${FOCUS_DEPLOY_HOST:?set FOCUS_DEPLOY_HOST (user@server) in site/deploy.env}"
: "${FOCUS_DEPLOY_KEY:?set FOCUS_DEPLOY_KEY (path to the ssh key) in site/deploy.env}"
KEY="$FOCUS_DEPLOY_KEY"
HOST="$FOCUS_DEPLOY_HOST"
URL="${FOCUS_SITE_URL:-https://how2me.me/focusapp}"

./build.sh
APK=$(ls public/*.apk | head -1 | xargs basename)

# One version = one binary. If this version already has a release on GitHub (the Publish workflow
# may have made it), the file on the site has to be that release's APK, not a second build.
VERSION=${APK#focus-launcher-}; VERSION=${VERSION%.apk}
if command -v gh >/dev/null 2>&1 && released=$(gh release download "v$VERSION" --pattern "$APK.sha256" --output - 2>/dev/null | cut -d' ' -f1) && [ -n "$released" ]; then
  if [ "$released" != "$(shasum -a 256 "public/$APK" | cut -d' ' -f1)" ]; then
    echo "v$VERSION is already released with a different APK. Publish that file instead:" >&2
    echo "  gh release download v$VERSION --pattern '$APK' --dir /tmp && FOCUS_APK=/tmp/$APK site/deploy.sh" >&2
    exit 1
  fi
fi

COPYFILE_DISABLE=1 tar --no-xattrs -C public -czf - . 2>/dev/null |
  ssh -i "$KEY" -o BatchMode=yes "$HOST" 'tar -xzf - -C /var/www/focusapp && find /var/www/focusapp -type f -exec chmod 644 {} +'

# Trust nothing: fetch what visitors get and compare it with what was built.
served=$(curl -fsS -m 60 "$URL/$APK" | shasum -a 256 | cut -d' ' -f1)
built=$(shasum -a 256 "public/$APK" | cut -d' ' -f1)
code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' "$URL/")
if [ "$served" = "$built" ] && [ "$code" = "200" ]; then
  echo "live: $URL/   ($APK, sha256 $built)"
else
  echo "DEPLOY CHECK FAILED: page=$code served=$served built=$built" >&2; exit 1
fi

# Tell the search engines that take direct submissions (IndexNow: Bing, Yandex, Seznam, Naver)
# that the page changed. Google does not take part; it relies on the sitemap in robots.txt and on
# Search Console. Sends nothing but the public URL and the public key.
KEYID=$(cat src/indexnow-key.txt)
ping=$(curl -s -m 25 -o /dev/null -w '%{http_code}' -G "https://api.indexnow.org/indexnow" \
  --data-urlencode "url=$URL/" --data-urlencode "key=$KEYID" --data-urlencode "keyLocation=$URL/$KEYID.txt" || true)
echo "IndexNow: HTTP $ping (200/202 = accepted)"
