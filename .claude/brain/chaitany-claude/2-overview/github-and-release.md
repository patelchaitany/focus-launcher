# Tier 2 · The public repository, signing, releasing

Repo: **https://github.com/patelchaitany/focus-launcher** · public since 2026-09-19 · branch `main`
· `gh` is authenticated as the owner on his machine. Homepage field and README point to the site;
topics set (minimalist-launcher, android-launcher, app-blocker, …).

## What is deliberately not in the repo
All git-ignored and verified absent from raw.githubusercontent.com:
`keystore.properties` · `local.properties` · `site/deploy.env` · `site/public/` (generated, holds
the APK) · everything in `.claude/` **except** `CLAUDE.md` and the brain · every `private/` folder
inside a brain · build output. The committed template for the deploy target is
`site/deploy.env.example`.

The brain itself **is** committed (owner's decision, 2026-09-19). That is why it must never contain
what the list in `.claude/CLAUDE.md` → "The brain is public" forbids.

## Before every push (publishing cannot be undone)
```bash
git add -A
# 1. no forbidden files
git diff --cached --name-only | grep -E "keystore\.properties|local\.properties|deploy\.env$|\.jks$|\.apk$|/private/|^\.claude/launch\.json|^site/public/" && echo STOP
# 2. no forbidden content: generic patterns ...
git diff --cached | grep -cE "^\+.*(/Users[/]|(storePassword|keyPassword)[=][^%\$\"' ]|PRIVATE[ ]KEY)"   # brackets: so this line does not match itself; a value after "=" counts, a %s or $VAR does not
# 3. ... and the owner-specific ones, kept out of the repo on purpose (count only, never print matches)
git diff --cached | grep -cEf .claude/brain/chaitany-claude/private/audit-patterns.txt
```
Step 1 must print nothing; steps 2 and 3 must print `0`. Step 3 uses `-c` so that a hit is never
echoed into a log. A `0` only means something if there was input: `git diff --cached | wc -l`
must be greater than zero, or the check read nothing. `private/audit-patterns.txt` is generated from the machine's own config by
`private/make-audit-patterns.sh`; regenerate it when the server, key or device changes. Then
commit and push. No force-pushes to `main` without being asked.

## Commits
Use the owner's global git identity as configured on his machine (the author address is visible
in public history; he was told about GitHub's noreply alternative). End every commit message with
the `Co-Authored-By:` line the session instructions specify. Commit or push only when asked, or as
the closing step of a change he asked to have published.

## Signing and the three builds
| Build | Signed with | For |
| --- | --- | --- |
| `debug` | debug key | short verification (`run-as`, exported test activities) |
| `release` | **debug key**, R8-optimized | the owner's own phone (installs over debug, keeps data) |
| `dist` | **release key**, same optimized code | the website; the only build that may be published |

The release key lives outside the repo. Its location and password are in git-ignored
`keystore.properties` (never print it). If key or password is lost, no published build can ever be
updated: the owner was told to back both up. A `dist` APK cannot be installed over
`release`/`debug` (or the reverse).

## CI and the release page (since 2026-09-19)
- **CI**: `.github/workflows/build.yml` runs tests, lint and an optimized build on every push to
  `main` and every pull request, and attaches the APK to the run. It uses **no secrets**: that APK
  is signed with a throwaway runner key and is not the official download. Brain, notes and site
  changes do not trigger it.
- **Releases**: https://github.com/patelchaitany/focus-launcher/releases , tag `v<version>`, with
  the very APK the website serves (same SHA-256) plus its `.sha256`. `verify-release.yml` fails
  if an attached APK is not signed with the release key.
- **Publishing from CI** (owner's choice, 2026-09-20: automatic, with his approval):
  `.github/workflows/publish.yml` signs with the real key, uploads the site and creates the
  release, but only in the protected environment `release`, whose secrets GitHub hands out only
  to runs the owner approved, and only from `main`. The build workflow still never sees the key.
  It is switched on by the owner running `site/setup-ci-publishing.sh` once (**never run that
  script as an agent**: it handles his keys); until then the workflow skips itself.
- **Versions** are `<baseVersion>.<number of commits>`; the build number is the `versionCode`.
- **Collaborators' pull requests** are reviewed before anything is built for the phone or the
  public download. Checklist and reasoning: `3-details/ci-and-releases.md`.

## F-Droid (asked for 2026-09-20)
F-Droid builds from source from a recipe in its own GitLab repository; inclusion is a one-time
merge request there, updates then follow this repo's `vX.Y.Z` tags by themselves. Here:
`LICENSE` (GPL-3.0-or-later, the owner's choice), `fastlane/metadata/android/en-US/` (listing),
`fdroid/com.focus.launcher.yml` (recipe template, reproducible build with the project's own
signature) and the manual workflow `.github/workflows/fdroid.yml` (check with F-Droid's tools;
submit only with the owner's GitLab token, after his approval).
**Where it stands (2026-09-20): the app is not on F-Droid and nothing has been submitted.** All
four runs of the workflow were check-only (the `submit` job skipped in each); fdroiddata has no
`metadata/com.focus.launcher.yml` and no merge request exists. How to run the workflow, and the
three commands that answer "are we on F-Droid yet": `3-details/fdroid.md`.

## Releasing a new version
With CI publishing on: merge to `main`, the owner approves the waiting Publish run, done (site,
release page, checksums). Bump `baseVersion` when the change deserves a new 1.x. By hand, from
the machine that has the key:
1. Decide whether `baseVersion` in `app/build.gradle.kts` changes (the build number is automatic).
2. `site/clean-build.sh`: tests, lint and the signed APK **from a clean worktree, without the
   build cache**. Never publish an APK built in the working folder: it is not reproducible
   (`3-details/fdroid.md`), and F-Droid only ships our APK if its own build is identical.
3. Owner's phone: `assembleRelease` → `adb install --user 0 -r` → `compile -m speed-profile -f`.
4. If the app's look changed, update the site's copy and mockups; preview `site/public` locally.
5. Audit, commit, push (a pull request lets CI prove itself first), merge.
6. `git tag v<version>` → `gh release create` with the APK and `.sha256` from `site/public/`.
7. Public: `FOCUS_APK=$(site/clean-build.sh | tail -1) site/deploy.sh` (the APK file name carries
   the version; older APKs stay on the server so old links keep working).

## Open
CI publishing is built but waits for the owner to run the setup script · the F-Droid merge
request waits for the owner's GitLab account, fork and token: until it is opened and merged,
Focus is not on F-Droid.

## Tier 3 pointers
`ci-and-releases.md` · `fdroid.md` · `signing-keys.md` · `toolchain-and-build.md` · `brain-upkeep.md` (the
brain is part of the repo)
