# Tier 3 · Mistakes made here, and platform traps

Each entry: symptom → cause → fix / rule. Add to this whenever something costs time.

## Product judgement
- **Put a limit on mail, browser and messenger apps.** Trusted `CATEGORY_SOCIAL`, which covers
  communication apps. → Classify by curated lists + intent probes; never guess towards a limit.
  (`app-classification.md`)
- **Dimmed on press; the owner wanted it to light up.** → Ask what feedback should feel like
  before inventing it; the first version was rejected.
- **Clip-reveal launch animation made launches feel slow**, worst from the ring. → A launch
  animation must cover the screen from the first frame. (`ui-system.md`)
- **Granting calendar permission did nothing visible**, reported as "calendar not working". →
  A permission grant must produce the thing it was granted for (turn the section on).
- **Auto-picked an empty calendar.** "First primary" is often a dead local account. → Pick by
  evidence (most upcoming events).
- **A setting alone is not discoverable.** "Tap the ring to open an app" was asked for after it
  already existed in Settings. → Put the customization on the object (long-press), like the
  shortcuts.

## Correctness
- **An app that stacks activity instances was under-counted by a third.** Pairing usage events
  by class name. → Counter per package, ignore STOPPED, cover-grace safety net.
  (`usage-tracking.md`)
- **Time in Settings vanished from screen time.** `com.android.settings` has a HOME activity
  (`FallbackHome`, priority −1000). → `homePackages()` keeps `priority >= 0` only.
- **My ground truth was wrong, not the app.** `dumpsys usagestats` dumps *all users*; a second
  profile's minutes made a correct total look short. Also its daily bucket does not start at
  local midnight. → Split on `user=N`; compute since local midnight yourself.
- **Chased a phantom keyboard bug.** "Open keyboard right away" had been switched on in the app's
  settings while I was testing. → Read the saved settings and logcat before fixing "bugs" on a
  phone in use.
- **Property initialised after `init {}`** in `AppRepository` while `init` started a coroutine
  using it. → Declare fields above `init`; first `reload()` runs on another thread.

## Editing and scripting
- **Duplicated 150 lines of `UsageRepository.kt`** with a Python splice: my end anchor also
  occurred *earlier* in the file, so `s[:start] + new + s[end:]` repeated a chunk. → Assert every
  anchor is unique (`s.count(x) == 1`) **and** `start < end`; afterwards grep for duplicate
  function definitions.
- **Left placeholder lines to silence "unused" warnings** (`@Suppress … Modifier.width(0.dp)`)
  three times. → Remove the import instead; re-read generated code before moving on.
- **zsh does not word-split unquoted variables** (`$PKGS` arrived as one argument). → `xargs`.
  It bit twice. The second time it was inside an audit: `cat $FILES` failed, `grep -c` read
  nothing and reported a reassuring **0**. → A count-only check must also print how much it read
  (`wc -l`), and a file list goes through a file or `xargs -0`, never through an unquoted variable.
- **A version-catalog alias becomes Kotlin code.** `json-for-tests` turns into
  `libs.json.for.tests`, and `for` is a keyword: the build script does not compile. → No Kotlin
  keywords (`for`, `in`, `is`, `as`, `fun`, `val`, `class`, `object`…) as a segment of an alias.
- **SSH control socket path too long** (>104 bytes in the scratch dir): tunnel never started. →
  Background the ssh with `&`, keep `$!`, `kill` it.
- **`git check-ignore --no-index` outside a repo** reports everything as not ignored. → `git init`
  first, then check, then stage.

## Publishing
- **Wrote the brain as if it would stay private, then it was made public.** It held the server's
  address and inventory, device identifiers, and what I had seen on the owner's phone while
  testing. Cleaning that up meant rewriting twelve files. → Write every note as if it will be
  published: mechanisms and decisions in the tiers, anything that identifies a machine, an account
  or a person's habits in git-ignored `private/` from the start.
- **A pre-push audit that contains the strings it searches for publishes them.** The first audit
  command had part of the server address in its regex. → Owner-specific patterns live in
  `private/audit-patterns.txt`, generated from local config, and the audit prints counts only.
- **The documented audit matched itself** (its own regex text is an added line containing the
  generic patterns), so a clean push reported 1 hit. → Write such patterns in bracket form
  (`storePassword[=]`): they still match the real thing, not their own source.
- **Explaining a clean-up by listing what was being removed** repeats the sensitive text in the
  conversation, and one such reply was stopped by a safety filter, costing a turn. → Count
  matches, copy mechanically, rewrite whole files; never quote the sensitive text.
- **raw.githubusercontent.com lags a push by a few minutes** (CDN cache): a file pushed seconds
  ago looked absent. → Verify fresh pushes through the API (`gh api …/contents/<path>`); use the
  raw host only for "is this path publicly reachable at all".
- **The brain covered the hard parts and skipped the plain ones.** After the first write-up there
  was no note for the weekly review, the long-press menu, the drawer or the Setup page, although
  all were asked for by name. → Check coverage against the feature list, not against what was
  difficult (`brain-upkeep.md`).

## Android platform
- `makeCustomAnimation` is ignored for task-level opens since Android 13; scale-up / clip-reveal
  are honoured.
- `TRIM_MEMORY_UI_HIDDEN` fires on every app launch from a launcher.
- `am force-stop` on the default launcher while the phone is locked leaves it stopped; start
  `MainActivity` explicitly.
- A composed-but-off-screen `BasicTextField` takes the window's initial focus and pops the
  keyboard: `focusProperties { canFocus = … }` + `stateAlwaysHidden`.
- Newest AndroidX may need a newer compileSdk/AGP than installed: read the AAR metadata first.
- Non-home activities started from adb do not join the home task (different activity types).
- **"Focus Settings" could not be opened from the drawer** (nor from any other launcher while
  Focus was the home app). `SettingsActivity` shared the home task's affinity, so a launcher-style
  start (`NEW_TASK | RESET_TASK_IF_NEEDED`) only brought the home task forward and delivered the
  intent to `MainActivity`: "Activity not started, intent has been delivered to currently running
  top-most instance". → `android:taskAffinity="com.focus.launcher.settings"` on `SettingsActivity`.
  Reproduce / verify: `am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
  -n com.focus.launcher/.SettingsActivity -f 0x10200000` while the home screen is in front.
- **It had already happened on the owner's phone**: every install before 2026-09-19 evening was
  a plain `adb install -r`, and a per-user check (`pm list packages --user <id>`) showed Focus in
  all of the phone's profiles. Nobody had looked. → After any install, list the package per user;
  taking it out of a profile again is an uninstall, so it is the owner's decision.
- **`adb install` without `--user` installs for every user, a work profile included.** Focus then
  shows up inside the work profile too, which the phone's owner noticed at once. → Always
  `adb install --user 0 …`; check with `pm list packages --user <id> com.focus.launcher`.
- Work-profile calendars/usage are blocked by policy for personal-side apps; respect it.

## GitHub
- **Published an APK that nobody else could rebuild.** `assembleDist` in the everyday working
  folder (incremental Kotlin state, Gradle build cache) kept one class that a clean build of the
  same commit does not have. Two earlier comparisons had passed, so it looked deterministic. →
  Releases come from `site/clean-build.sh` (fresh worktree, `--no-build-cache`) or from CI, never
  from the working folder. "It matched twice" is not "it is reproducible": compare against a
  clean build.
- **A tool that exits 0 on failure makes a green run meaningless** (`fdroid build`). → When a
  step's verdict matters, find where the tool states it and test for that.
- **`stat -f %z FILE || stat -c %s FILE` is not a portable "file size".** On Linux `stat -f` means
  "file system": it succeeds and prints file-system facts, so the fallback never runs.
  `site/build.sh` fed that text into Python and died, but only on the runner. → `wc -c < FILE`.
  More generally: a script that will run in CI must be run in CI before the day it matters; the
  build workflow now builds the site on every run for exactly that reason.
- **zsh and unquoted variables, fourth time** (`G="./gradlew -D…"; $G task` ran nothing, and a
  stale APK from the build before made it look as if the flag had no effect). → A command with
  arguments is a shell *function* here, never a string. And delete old outputs before a build
  whose output you are going to inspect.
- **zsh and unquoted variables, third time:** `set -- $spec` in a loop did not split, every API
  path was wrong and everything answered 404, which looked like a permissions problem. → In this
  shell, write a function with real arguments instead of splitting a string.
- **`release` workflows run from the default branch's copy of the workflow file.** A release
  published before `verify-release.yml` was on `main` would not have been checked. → Merge the
  workflow first, publish the release after.
- **`gh api repos/<other>/<repo>/git/ref/tags/<tag>` answers 404 with this token** although the
  tag exists; `…/tags` lists them. → Look action versions up with the list endpoint, and do look
  them up: the majors had moved well past what I remembered.
- **A published APK cannot be rebuilt byte for byte**, and `app/build/outputs` is overwritten by
  the next build. → Copy the current `dist` APK aside before bumping the version; that copy is
  what made a `v1.0` release possible afterwards (its checksum matched the server's).
- **A Claude cloud session cannot reach f-droid.org** (the egress proxy answers 403 to the
  CONNECT, and WebFetch reports the domain blocked), so "is Focus in the catalogue?" cannot be
  asked there directly. → gitlab.com is reachable: whether a recipe is in fdroiddata answers the
  same question one step earlier (`fdroid.md`), and f-droid.org's package API is for the owner's
  own machine. Never report "not published" from a blocked request alone; say which check ran.
- **The cloud checkout has no tags and no `private/` folder**: `git tag` is empty (so
  `git rev-list <tag>` exits 128) and step 3 of the pre-push audit has no pattern file. → Read
  tags through the GitHub API, and say in the report that step 3 of the audit could not run.

## Web
- **CSS container units on the container itself** resolve against the *ancestor* (the viewport):
  padding in `cqw` on `.phone` left a 126px content box and tiny mockups. → Inner `.screen`.
- `pathLength` on SVG circles is ignored by `rsvg-convert` (broken dashes in the OG image). →
  Real dash lengths for rasterised SVGs.
- `scroll-behavior: smooth` makes screenshots taken right after `scrollTo` blank or stale. →
  Set it to `auto` before scripted scrolling.
- nginx `add_header` in a location replaces inherited headers; `alias` + `try_files` misbehaves
  (use `root`); the stock nginx `mime.types` has no entry for `.apk`.

## Measuring
- PSS is not comparable across process ages (95 MB fresh vs 44 MB settled). CPU in a window where
  someone is swiping measures them, not the app. `dumpsys gfxinfo`/`meminfo` themselves cost the
  app a few ms. → Measure with the screen off or during a confirmed quiet window; say which.
