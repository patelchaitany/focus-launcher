# Tier 3 · Toolchain and build

## The development machine (macOS, Homebrew)
- Default `java` is **JDK 25**, which Gradle 8.14 cannot run on. `gradle.properties` pins
  `org.gradle.java.home` to Homebrew `openjdk@21`. Homebrew JDKs (17, 21, 25) are not registered
  with `/usr/libexec/java_home`. No Android Studio, no system `gradle`: the wrapper was generated
  from the cached distribution in `~/.gradle/wrapper/dists/gradle-8.14.3-bin/…`.
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (platforms 31/34/35/**36**,
  build-tools 35/36; `local.properties` points here). `~/Library/Android/sdk` only has 35.
- Tools: `adb`, `apksigner` and `aapt2` in build-tools 36.0.0, `keytool` from JDK 21,
  `rsvg-convert`, ImageMagick, Python 3 with Pillow, `gh`.

## Versions, and why they are one step behind the newest
Gradle 8.14.3 · AGP 8.13.2 · Kotlin 2.3.21 · Compose BOM 2026.06.01 (Compose 1.11.4) · core-ktx
1.18.0 · activity-compose 1.12.4 · lifecycle 2.10.0 · coroutines 1.10.2 · profileinstaller 1.4.1 ·
JUnit 4.13.2. compileSdk = targetSdk = 36, minSdk 26 (java.time, ApplicationInfo.category).
The newest AndroidX (Compose 1.12, core 1.19, lifecycle 2.11) declares `minCompileSdk=37` and
`minAndroidGradlePluginVersion=9.1.0`, which needs platform 37 + Gradle 9. How to check a
candidate before adopting it: download the AAR and read
`META-INF/com/android/build/gradle/aar-metadata.properties`. Lint's "newer version available"
warnings are therefore expected; do not "fix" them by bumping blindly.

## Build types
`debug` · `release` (R8 + resource shrinking, **debug key**) · `dist` (`initWith(release)`,
release key via `keystore.properties`, `matchingFallbacks += "release"`). `src/debug/
AndroidManifest.xml` exports `ReviewActivity` and `BlockActivity` for adb in debug builds only.
APK ≈ 1.34 MB (debug ≈ 9.5 MB). `androidResources.localeFilters += "en"`: the app has one
language, and without the filter the AndroidX libraries ship their strings in 85 (resources.arsc
93 KB → 19 KB compressed). Add a locale there the day Focus is translated. `-opt-in=androidx.compose.foundation.ExperimentalFoundationApi`.

## Checks that must stay green
```bash
./gradlew :app:testDebugUnitTest   # 19 tests: ForegroundTrackerTest (13), StripEmojiTest (6)
./gradlew :app:lintDebug           # 0 errors; report: app/build/reports/lint-results-debug.txt
```
Compose lints that have bitten: `NonObservableLocale` (use `currentLocale()` from
`HomeWidgets.kt`), `StateFlowValueCalledInComposition` (collect it), `UseKtx`
(`prefs.edit { }`, `"…".toUri()`, `color.toDrawable()`).

## Manifest notes
Permissions: PACKAGE_USAGE_STATS, QUERY_ALL_PACKAGES (a launcher must see every app; also lets
the service resolve any activity), REQUEST_DELETE_PACKAGES, POST_NOTIFICATIONS, READ_CALENDAR,
EXPAND_STATUS_BAR. Services: the accessibility service, and `MediaListener` (notification access, optional, empty). **No INTERNET**: a public promise (site, README), never add it.
`MainActivity`: HOME + DEFAULT, `singleTask`, `clearTaskOnLaunch`, `excludeFromRecents`,
`stateAlwaysHidden|adjustResize`, portrait. `SettingsActivity`: own task affinity (`…settings`), or a launcher cannot open it (see
`mistakes-and-lessons.md`). `BlockActivity`: `singleInstance`, own task affinity,
excluded from recents. Non-home activities started from adb land in a *separate* task from the
home task (activity types differ); from inside the app they stack on the home task and a Home
press clears them.

## Portability wart (open)
The committed `org.gradle.java.home` path breaks a fresh clone on another machine until removed.
**Building on another machine without editing tracked files:** override the pin on the command
line, `./gradlew -Dorg.gradle.java.home=<a JDK 17–21 home> …`, and point at the SDK with
`ANDROID_HOME` or a git-ignored `local.properties` (`sdk.dir=…`). Platform 36 must be installed.
A portable fix would be Gradle daemon-JVM criteria + the foojay resolver (downloads a JDK); not
done, and untested whether the 8.14 client even starts under JDK 25.
