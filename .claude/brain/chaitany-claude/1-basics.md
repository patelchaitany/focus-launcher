# Tier 1 · Basics (read all of it, every time)

**Focus** is a text-only, strictly black-and-white Android launcher (Kotlin + Jetpack Compose, no
Material, package `com.focus.launcher`, 35 Kotlin files, ~7,000 lines). Home = battery ring clock,
today's screen time in words under it, one-calendar agenda, up to 5 fast apps, 2 corner shortcuts.
Swipe left = searchable app list (sortable; Personal / Work tabs), swipe right = the phone's web
search, double tap = lock. Social apps and games get daily timers that lock the app; a weekly
review shows where the time went. No INTERNET permission. Built for, and used daily by, its owner.

**Owner:** Chaitany (GitHub `patelchaitany`). His phone runs Android 16 with Focus as its default
launcher. He wants things done end to end and verified, and honest reports of what was not.

**This brain is public** (it is committed; the repo is public). Anything about his server, his
device or what is on his phone lives only in the git-ignored `private/` folder next to this file.

## Rules that prevent damage
1. **The phone is in use while you test.** Never inject blind taps or swipes. Open screens by
   intent; screenshot only when `topResumedActivity` is `com.focus.launcher/`.
2. **Never grant special access over adb** (usage access, accessibility, default home). The in-app
   Setup page sends the owner to each switch.
3. **The repo is public.** Before every push, run the audit in `2-overview/github-and-release.md`.
   Never commit `keystore.properties`, `local.properties`, `site/deploy.env`, `site/public/`,
   `.claude/launch.json`, or anything under a `private/` folder.
4. **Never publish anything observed on the phone**: screenshots, app lists, usage, calendar.
   Site mockups and examples in this brain use invented content.
5. **Never print or log the keystore password.** It lives in git-ignored `keystore.properties`.
6. **Server changes are surgical:** back up, `sudo nginx -t`, reload, roll back on failure. The
   server is shared with the owner's other projects.
7. **Never auto-limit communication tools** (mail, browsers, messengers). Never guess towards a limit.
8. **Everything stays monochrome and icon-free,** including emoji in third-party text. (One drawn
   exception, asked for by a contributor: the work-profile briefcase, `WorkBadge`.)
9. **Do not work around a managed work profile's restrictions** (calendar, usage). Explain the limit.
10. **Never promise search rankings.** Say what was done and what it depends on.

## Where things are
| What | Where |
| --- | --- |
| App source | `app/src/main/java/com/focus/launcher/` (`data/`, `service/`, `ui/`) |
| Unit tests | `app/src/test/…/data/` (ForegroundTracker 13, StripEmoji 6) |
| Website source / output | `site/src/` → `site/public/` (generated, ignored) |
| nginx rules for the site | `site/nginx-focusapp.conf` (installed on the server as a snippet) |
| Signing key / its password | outside the repo / both named in `keystore.properties` (ignored) |
| Deploy target | `site/deploy.env` (ignored) |
| Server, device and phone specifics | `private/` (ignored) |
| CI · publish · release check | `.github/workflows/` `build.yml` · `publish.yml` · `verify-release.yml` |
| Live site · repo · releases | https://how2me.me/focusapp/ · https://github.com/patelchaitany/focus-launcher · `/releases` |

## Commands
```bash
./gradlew :app:testDebugUnitTest :app:lintDebug      # must stay: all tests pass, lint 0 errors
./gradlew :app:assembleRelease                        # optimized, DEBUG-key signed: for the owner's phone
adb install --user 0 -r app/build/outputs/apk/release/app-release.apk   # --user 0: not into a work profile
adb shell cmd package compile -m speed-profile -f com.focus.launcher
./gradlew :app:assembleDist && site/deploy.sh         # public APK (release key) + site, verified
```
Three build types: `debug`, `release` (owner's phone), `dist` (public). `dist` cannot be installed
over `release` or the reverse: different signatures. JDK: Gradle 8.14 cannot run on JDK 25;
`gradle.properties` pins a JDK 21 path.

## State of the world (2026-09-19, late evening)
**Version 1.1** (`versionCode` 2) is on the owner's phone (release build, speed-profile), on the
site, and on the GitHub release page next to 1.0. It contains a collaborator's merged PR #1
(drawer sort and Personal / Work tabs, swipe right = web search, a drawn work badge, today's
screen time in words instead of the 24-hour bar on home). The owner asked for it to be shipped;
the points in it that touch his earlier decisions still wait for his word (open decisions 10–14).
CI builds every push and pull request. **CI publishing** (site + release page, after the owner's
approval) is built but off until he runs `site/setup-ci-publishing.sh`; agents never run it.
Versions are `<base>.<commit count>` from 2026-09-20 on.
The accessibility service has **not been enabled** on the phone, so mid-session locking and double
tap to lock are untested there. Google Search Console not done. **No LICENSE** (owner to decide).
The brain is committed and public, and was fact-checked against the code, the repo and the live
site on 2026-09-19 (`3-details/brain-upkeep.md`). Publishing happens on request: `git status`
shows whether there is unpushed work.

## Tier 2 index: read the area(s) you will touch
- `2-overview/user-and-decisions.md` — what the owner asked for and decided; open decisions
- `2-overview/app.md` — architecture, features and where each lives
- `2-overview/device-testing.md` — building, installing, verifying on the phone safely
- `2-overview/website-and-server.md` — the site, the server, deployment, search
- `2-overview/github-and-release.md` — the public repo, CI, releases, signing, what stays private
