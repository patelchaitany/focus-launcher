#!/bin/bash
# Assembles site/public from site/src plus the signed APK.
# Usage: site/build.sh            (expects ./gradlew :app:assembleDist to have been run)
#        FOCUS_APK=/path/to.apk site/build.sh     (publish that APK instead, e.g. a release asset)
set -euo pipefail
cd "$(dirname "$0")"
ROOT=..
APK_SRC="${FOCUS_APK:-$ROOT/app/build/outputs/apk/dist/app-dist.apk}"
[ -f "$APK_SRC" ] || { echo "Build the APK first:  ./gradlew :app:assembleDist" >&2; exit 1; }

# The version on the page is read out of the APK itself, so the two can never disagree.
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null || true)}"
AAPT2=$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)
[ -n "$AAPT2" ] || { echo "aapt2 not found: set ANDROID_HOME, or sdk.dir in local.properties" >&2; exit 1; }
VERSION=$("$AAPT2" dump badging "$APK_SRC" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
[ -n "$VERSION" ] || { echo "could not read versionName from $APK_SRC" >&2; exit 1; }
APK_FILE="focus-launcher-$VERSION.apk"

rm -rf public && mkdir -p public
cp "$APK_SRC" "public/$APK_FILE"
cp src/style.css src/favicon.svg public/
# Ownership proofs for Google Search Console / Bing Webmaster Tools: drop the file they give you
# into site/src and it is published as is (e.g. google1a2b3c4d5e6f.html, BingSiteAuth.xml).
for proof in src/google*.html src/BingSiteAuth.xml; do [ -f "$proof" ] && cp "$proof" public/; done
rsvg-convert -w 1200 -h 630 src/og.svg -o public/og.png
rsvg-convert -w 180 -h 180 src/favicon.svg -o public/icon-180.png

SHA256=$(shasum -a 256 "public/$APK_FILE" | cut -d' ' -f1)
# Not `stat`: on Linux `stat -f` means "file system", succeeds, and prints something else entirely.
BYTES=$(wc -c < "public/$APK_FILE" | tr -d ' ')
SIZE=$(python3 -c "print(f'{$BYTES/1048576:.1f} MB')")
echo "$SHA256  $APK_FILE" > "public/$APK_FILE.sha256"

VERSION="$VERSION" APK_FILE="$APK_FILE" SHA256="$SHA256" SIZE="$SIZE" BYTES="$BYTES" python3 - <<'PY'
import datetime, html as htmllib, json, os, pathlib, re

BASE = 'https://how2me.me/focusapp/'
env = {k: os.environ[k] for k in ('VERSION', 'APK_FILE', 'SHA256', 'SIZE', 'BYTES')}
page = pathlib.Path('src/index.html').read_text()
text = lambda fragment: re.sub(r'\s+', ' ', htmllib.unescape(re.sub(r'<[^>]+>', '', fragment))).strip()

# Structured data, derived from the page itself so the two can never disagree.
description = re.search(r'<meta name="description" content="([^"]*)"', page).group(1)
faq = [(text(q), text(a)) for q, a in re.findall(r'<summary>(.*?)</summary>\s*<p>(.*?)</p>', page, re.S)]
assert len(faq) >= 5, 'FAQ entries not found'
app = {
    '@context': 'https://schema.org',
    '@type': 'MobileApplication',
    'name': 'Focus',
    'alternateName': ['Focus Launcher', 'Focus minimalist launcher'],
    'description': description,
    'url': BASE,
    'image': BASE + 'og.png',
    'operatingSystem': 'Android 8.0 and up',
    'applicationCategory': 'UtilitiesApplication',
    'applicationSubCategory': 'Minimalist launcher',
    'softwareVersion': env['VERSION'],
    'fileSize': env['SIZE'],
    'downloadUrl': BASE + env['APK_FILE'],
    'installUrl': BASE + env['APK_FILE'],
    'isAccessibleForFree': True,
    'offers': {'@type': 'Offer', 'price': '0', 'priceCurrency': 'USD'},
    'permissions': 'Usage access; accessibility service (optional); notifications (optional); calendar (optional). No internet permission.',
    'featureList': [
        'Text-only, black and white home screen without icons',
        'Daily time limits that lock social media apps and games',
        'Today\'s screen time in plain words on the home screen, hour by hour in the review',
        'Weekly screen time review',
        'App search, rename and hide',
        'No internet permission, no account, no ads',
    ],
}
questions = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    'mainEntity': [{'@type': 'Question', 'name': q, 'acceptedAnswer': {'@type': 'Answer', 'text': a}} for q, a in faq],
}
# "</" inside a JSON string would end the script element early; escape it the standard way.
dump = lambda data: json.dumps(data, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
jsonld = '\n'.join('<script type="application/ld+json">' + dump(d) + '</script>' for d in (app, questions))

values = dict(json.loads(pathlib.Path('src/fragments.json').read_text()))
values.update({k: env[k] for k in ('VERSION', 'APK_FILE', 'SHA256', 'SIZE')})
values['JSONLD'] = jsonld
for key, value in values.items():
    page = page.replace('{{' + key + '}}', value)
assert '{{' not in page, 'unfilled placeholder left in index.html'
pathlib.Path('public/index.html').write_text(page)

# Discovery files.
today = datetime.date.today().isoformat()
pathlib.Path('public/sitemap.xml').write_text(
    '<?xml version="1.0" encoding="UTF-8"?>\n'
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    f'  <url><loc>{BASE}</loc><lastmod>{today}</lastmod></url>\n'
    '</urlset>\n')
# Served as https://how2me.me/robots.txt (the domain had none). It restricts nothing; it only
# tells crawlers where the sitemap is.
pathlib.Path('public/robots.txt').write_text(f'User-agent: *\nAllow: /\n\nSitemap: {BASE}sitemap.xml\n')
# IndexNow (Bing, Yandex, Seznam, Naver...): a public key file proves we may submit these URLs.
key = pathlib.Path('src/indexnow-key.txt').read_text().strip()
pathlib.Path(f'public/{key}.txt').write_text(key)
print(f'  structured data: MobileApplication + FAQPage ({len(faq)} questions); sitemap lastmod {today}')
PY
echo "built site/public:  $APK_FILE  $SIZE  sha256=$SHA256"
ls -la public | awk 'NR>1 {printf "  %9s  %s\n", $5, $NF}' | grep -v -E " \.$| \.\.$"
