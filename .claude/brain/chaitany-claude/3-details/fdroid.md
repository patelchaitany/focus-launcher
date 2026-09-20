# Tier 3 · F-Droid

Asked by the owner on 2026-09-20: submit the app following F-Droid's quick start guide, and
"create another CI to push it on fdroid and make it run manually".

## How F-Droid works (it is not an upload)
F-Droid builds every app itself, from source, from a recipe in **its** repository
(gitlab.com/fdroid/fdroiddata, `metadata/com.focus.launcher.yml`). The recipe gets there once, as
a merge request from a GitLab account. After it is merged, F-Droid's bot finds new versions from
this repository's tags and builds them; nothing is pushed per release. The listing (name,
descriptions, icon, screenshots, changelogs) is read from `fastlane/metadata/android/en-US/` here.

## What inclusion needed, and what was done
- **A FOSS license.** There was none. The owner chose **GPL-3.0** from three options
  (2026-09-20); recorded as `GPL-3.0-or-later` (LICENSE = GPLv3 text, the "or later" is stated in
  the README). The collaborator's merged code is part of the app: he was asked in the pull request
  to confirm the license for his contributions.
- **Only free dependencies:** AndroidX, Compose, Kotlin, coroutines (all Apache-2.0); `org.json`
  in tests only. No Play services, no analytics, no INTERNET permission. No AntiFeatures apply.
- **No local paths in the build:** the JDK pin left `gradle.properties` (it would have failed on
  F-Droid's server, as it did on any other machine).
- **Fastlane files:** title "Focus Launcher", a 73-character summary, description, 512 px icon
  rendered from the app's own vector icon, four screenshots drawn by `fastlane/screenshots.py`
  with invented content (never captured: rule 4), `changelogs/<versionCode>.txt` (≤ 500 chars).

## Versions without running Gradle
F-Droid finds versions by regex, never by running the build, and this project computes
`versionCode` from the commit count. Both are reconciled by the tag: a release is tagged
`v<base>.<count>`, and the recipe reads both numbers from the tag name:
`UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` and
`UpdateCheckData: '|^v\d+\.\d+\.(\d+)$||^v([\d.]+)$'` (empty file fields = "use the tag").
The build on F-Droid's side computes the same number, because its clone has the full history.
The workflow refuses a tag whose last component is not the commit count at that commit.
`v1.0` and `v1.1` do not match the pattern and are ignored.

## Reproducible builds: F-Droid ships the APK signed with the project's key
Without this F-Droid signs with its own key, its copy and the website's copy cannot update each
other, and F-Droid says the choice cannot be changed later. So it was tested before submitting:
- Same commit, `assembleRelease`, GitHub's Linux runner (Temurin 21) vs macOS (Homebrew JDK 21):
  119 entries, identical order, **one** difference: `META-INF/version-control-info.textproto`,
  the git stamp AGP 8.3+ adds. → `vcsInfo { include = false }` on the release type.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }`: the Google-only encrypted
  dependency blob lives in the signing block; F-Droid wants it out.
- `-Pfocus.unsigned` makes `release` unsigned. Its contents are identical, entry for entry, to the
  debug-signed `release` and to the release-key `dist` build that is published (checked), so
  F-Droid can build plain `gradle: yes` + `gradleprops: focus.unsigned` and compare with the
  published file.
- Recipe: `Binaries: …/releases/download/v%v/focus-launcher-%v.apk` and
  `AllowedAPKSigningKeys: <release certificate SHA-256>`.
If a release ever fails the comparison, F-Droid does not publish that version: fix and tag again.
- **The first real comparison failed, and the build was not the culprit.** F-Droid's container
  built 1.1.25 fine, but its `classes.dex` had one class fewer than the published APK (a small
  settings enum that R8 had kept), so the baseline profiles differed too. A build of the same tag
  from a **fresh worktree with `--no-build-cache`** on the owner's Mac was identical to F-Droid's,
  file for file. The published APK had been built in the long-lived working folder, with its
  incremental-compilation state and build cache. → **A published APK is always built by
  `site/clean-build.sh`** (clean worktree of HEAD, no build cache, tests, lint, checks the
  certificate and the permissions, prints the APK's path) and `publish.yml` builds with
  `--no-build-cache`. 1.1.22 and 1.1.25 stay as they are (one version, one binary); the first
  reproducible release is the next one.
- **1.1.29 is the first reproducible release, and F-Droid's tools confirm it:** in the buildserver
  container (JDK 21, Gradle 8.14.3) `readmeta`, `rewritemeta`, `checkupdates` (picks `v1.1.29` from
  the tags), `lint` (no warnings) and `build` pass, the published APK is fetched, its signature is
  copied onto F-Droid's own build, and the log ends in "successfully verified". `rewritemeta` only
  moved the `Binaries` URL onto its own line; the template is kept in that canonical form.
- `fdroid build` exits 0 even when the build or the comparison fails: the workflow's first
  "green" run was not green. The verdict is read from its log ("Could not build app", "NOT
  verified"), and the APK it built is kept in the artifact so that a failure can be diffed.

## The manual workflow: `.github/workflows/fdroid.yml` ("F-Droid", `workflow_dispatch` only)
Inputs: `tag` (empty = newest `vX.Y.Z`), `build` (default on), `submit` (default off).
- **check:** verifies the tag/commit-count rule and the Fastlane files at that tag, fills
  `fdroid/com.focus.launcher.yml` (`@VERSION_NAME@`, `@VERSION_CODE@`, `@COMMIT@` = full hash),
  shallow-clones fdroiddata and fdroidserver, and runs in `registry.gitlab.com/fdroid/fdroidserver:
  buildserver`, as the guide does: `fdroid readmeta`, `rewritemeta`, `checkupdates --allow-dirty`,
  `lint`, and `fdroid build`, which with `Binaries` set also compares the result with the
  published APK. The recipe as F-Droid's tools left it, and `fdroid/MERGE_REQUEST.md`, become the
  artifact `fdroid-recipe`.
- **submit:** runs in the protected `release` environment (owner's approval). With
  `FDROID_GITLAB_TOKEN` (environment secret) and `FDROID_GITLAB_FORK` (repository variable,
  `<gitlab user>/fdroiddata`) it commits the recipe to the branch `com.focus.launcher` of the fork
  through GitLab's API (no git push from a shallow clone) and opens "New app: Focus Launcher"
  against fdroid/fdroiddata with the filled-in checklist, or says that the MR is already open.
  Without them it stops with the instruction. Agents do not create the GitLab account or enter
  the token: the owner does (`gh secret set FDROID_GITLAB_TOKEN --env release` prompts for it).

## Running the workflow, and reading its result
Nothing about F-Droid happens on a push; the workflow is `workflow_dispatch` only.
- **GitHub:** Actions → **F-Droid** → *Run workflow* → branch `main`; `tag` empty = the newest
  `vX.Y.Z` tag; `build` on (about 5 minutes, the whole check took 5:04 for v1.1.29); `submit` off.
- **From a machine with `gh`:** `gh workflow run fdroid.yml --ref main -f tag=v1.1.29 -f build=true
  -f submit=false`, then `gh run watch` (or `gh run list --workflow=fdroid.yml`).
- **Verdict:** the `check` job's own steps. Green means F-Droid's tools accepted the recipe and,
  with `build`, that F-Droid's build of the tag is identical to the published APK. The artifact
  `fdroid-recipe` keeps the recipe as `rewritemeta`/`checkupdates` left it, `MERGE_REQUEST.md`,
  `fdroid-build.log` and the APK F-Droid built (for a diff when the comparison fails).
- **`submit` ticked** additionally needs the secret `FDROID_GITLAB_TOKEN` in the `release`
  environment and the repository variable `FDROID_GITLAB_FORK`; the job waits for the owner's
  approval and, without those two, stops with the instruction. Run the check alone as often as
  you like: it uses no secrets and writes nothing outside the run.

## Is Focus on F-Droid? How to check, without an account
Three questions, in the order they become true. 1 and 2 work from anywhere; 3 needs a machine that
may reach f-droid.org (the Claude cloud sandbox may not, see `mistakes-and-lessons.md`).
```bash
# 1. Is the recipe merged? 200 = yes (F-Droid will build it), 404 = no.
curl -s -o /dev/null -w '%{http_code}\n' "https://gitlab.com/api/v4/projects/fdroid%2Ffdroiddata/repository/files/metadata%2Fcom.focus.launcher.yml?ref=master"
# 2. Is a merge request open (or was one ever made)? Empty list = never submitted.
curl -s "https://gitlab.com/api/v4/projects/fdroid%2Ffdroiddata/merge_requests?state=all&search=com.focus.launcher&in=title,description"
# 3. Is a build published? 200 with versions = in the catalogue, 404 = not.
curl -s -o /dev/null -w '%{http_code}\n' https://f-droid.org/api/v1/packages/com.focus.launcher
```
**Answer on 2026-09-20: no, and nothing has been submitted.** 1 = 404, 2 = empty, and all four runs
of the F-Droid workflow were `check` only — the `submit` job is `skipped` in every one of them
(the newest, on `main` at the v1.1.29 commit, is green with `build`). So the app is not on F-Droid
and no reviewer has ever seen it. What is missing is only step 1–3 below, which the owner does.

## What only the owner can do
1. A GitLab.com account; fork gitlab.com/fdroid/fdroiddata (public fork).
2. A personal access token with the `api` scope; store it and the fork's path as above.
3. Run the workflow with `submit` ticked, approve it, then answer the reviewers on GitLab.
   If GitLab asks for a phone number or card to run pipelines, F-Droid says not to give one and
   to leave a note in the merge request.
