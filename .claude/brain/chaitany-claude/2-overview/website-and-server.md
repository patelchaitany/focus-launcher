# Tier 2 · The website, the server, and being found

Live: **https://how2me.me/focusapp/** (since 2026-09-19). Static HTML + one stylesheet. No
JavaScript, no web fonts, no third-party requests, strict CSP. Black on white or white on black,
following the visitor's system theme. Headline is the owner's wording; never change it.

## Source and build
```
site/src/index.html        template with {{VERSION}} {{SIZE}} {{SHA256}} {{APK_FILE}} {{RING}} {{JSONLD}} …
site/src/style.css         phone mockups are HTML/SVG sized in container units (cqw)
site/src/fragments.json    generated SVG fragments (ring, day bar, charts)
site/src/og.svg favicon.svg indexnow-key.txt
site/build.sh              → site/public/: fills placeholders, copies the dist APK, writes sha256,
                             og.png, icon, JSON-LD (from the page itself), sitemap.xml, robots.txt,
                             IndexNow key file; publishes src/google*.html if present
site/deploy.sh             build → tar over ssh → re-download APK and compare sha256 → IndexNow ping
site/deploy.env            FOCUS_DEPLOY_HOST / FOCUS_DEPLOY_KEY (git-ignored; see deploy.env.example)
```
Deploy: `./gradlew :app:assembleDist && site/deploy.sh`. Preview locally with the launch config
`focus-site` (`.claude/launch.json`, a static file server on port 4173).

## The server (be surgical)
The owner's own Linux VM running nginx. It is **shared with his other projects**, so nothing
outside the Focus files and the one nginx snippet is ours to touch. Where it is, how to log in and
what else runs there: `site/deploy.env` and `private/server.md`. Neither is committed.
- The Focus files live in their own directory, outside the main site's root on purpose.
- The rules are one snippet (= `site/nginx-focusapp.conf`), pulled into the domain's existing
  server block by **one** `include` line. Timestamped backups are made before every change.
- The snippet also serves the domain's `/robots.txt` (there was none; ours only names the sitemap).
- Procedure for any nginx change: back up → install → `sudo nginx -t` → reload → roll back on
  failure → verify from outside **including that `https://how2me.me/` still returns 200**.
- The server has no `rsync`; `deploy.sh` streams a tar archive over ssh instead.

## Being found (the owner wants to show up for "minimalist launcher")
Done: search phrase in `<title>`, description and an eyebrow inside the `<h1>`; a "what a
minimalist launcher is" section; 10-question FAQ; JSON-LD `MobileApplication` + `FAQPage`;
sitemap; robots; IndexNow accepted (Bing/Yandex/Seznam/Naver); GitHub repo links to the page and
back. Not done: Google Search Console (owner's account), a link from the how2me.me homepage,
mentions on other sites. **Never promise rankings**: brand queries in days, long-tail in weeks,
the head term in months and only with inbound links.

## Keeping the page true to the app
The home mockup in the hero is hand-written HTML/CSS and must show what the published version
shows. 1.1 replaced the 24-hour bar on home with "Screen Time / total / N% of today"
(`.screen-time` in `style.css`); the copy, the FAQ-derived JSON-LD and the feature list in
`build.sh` were changed with it. Under the download button the page links to the GitHub release
page ("Every version, with checksums").

## Rules
No real phone screenshots on the page. No inline styles or scripts (CSP would block them; JSON-LD
data blocks are fine). Keep server details out of anything committed, this brain included.

## Tier 3 pointers
`server-nginx.md` · `seo.md` · `signing-keys.md` · `mistakes-and-lessons.md`
