# Tier 2 · Building, installing and verifying on the owner's phone

Device: the owner's everyday Android 16 phone, 360 × 797 dp (1080p class, high refresh rate), with
Focus as its default home. It has more than one Android user, one of them a **managed work
profile**. Model, serial and the user list: `private/device.md` (not committed).

## The situation
The phone is **in use while you test**: permissions get granted, apps pinned, settings changed and
screens dismissed while you work, and the phone locks itself after a short timeout. You cannot
unlock it.

## How to test safely
1. **No blind input.** A tap can land in whatever app is in front by then. Drive the app by intent:
   ```bash
   adb shell am start -n com.focus.launcher/.SettingsActivity --es route timers
   # debug builds only (src/debug manifest exports them):
   adb shell am start -n com.focus.launcher/.ReviewActivity
   adb shell am start -n com.focus.launcher/.BlockActivity --es package com.example.app --el used 1860000 --ei limit 30 --ez preview true
   ```
2. **Guarded screenshots only.** Capture only if
   `adb shell dumpsys activity activities | grep -m1 topResumedActivity` shows `com.focus.launcher/`
   and `isKeyguardShowing=false`. Otherwise you capture someone's private screen. Screenshots stay
   in the scratchpad; they are never committed or published.
3. **When locked, wait passively.** Run a background loop that polls for "unlocked + Focus in front"
   and then captures/measures. Installing works while locked.
4. **Read state before fixing.** Surprising state is usually a setting the owner changed, not a
   bug. Debug builds: `adb shell run-as com.focus.launcher cat shared_prefs/focus_settings.xml`.
   Release builds have no `run-as`.
5. **Never grant special access yourself** and never uninstall. Read-only checks are fine
   (`appops get … GET_USAGE_STATS`, `settings get secure enabled_accessibility_services`,
   `cmd role get-role-holders android.app.role.HOME`).
6. **Restarting the launcher:** `am force-stop` leaves it stopped if the phone is locked. Follow it
   with `adb shell am start -n com.focus.launcher/.MainActivity`.

## After an install, while the phone is in use
`adb install` kills the launcher's process; the system starts it again the next time the owner
goes home. Do not start `MainActivity` yourself while another app is in front: it would pull him
out of what he is doing. A passive loop does the rest: wait until `pidof com.focus.launcher`
has existed for ~20 s (profileinstaller has written the profile by then), run
`cmd package compile -m speed-profile -f`, and only if Focus is *not* in front `am kill` it so the
next start uses the compiled code. Then read `logcat -b crash` for the package (count only).
Always `adb install --user 0 -r`: a plain install goes into every profile on the phone.

## Which build goes on the phone
`release` (optimized, debug-key signed) + `cmd package compile -m speed-profile -f`. Debuggable
Compose is visibly janky and reads as "not smooth". `debug` only for short verification that needs
`run-as` or the exported activities; put `release` back afterwards.

## Verifying numbers
`adb shell dumpsys usagestats` contains the raw event log **with instance ids**: ground truth for
screen time. It dumps **all users** (split on `user=N`; compare with user 0) and its "daily" bucket
does not start at local midnight. Method and script outline: `3-details/usage-tracking.md`.
Performance probes (all passive): `3-details/performance.md`.
Whatever those dumps show about the owner's apps and habits is his: use it to check the numbers,
write down only the method and the size of the error.

## Tier 3 pointers
`usage-tracking.md` · `performance.md` · `mistakes-and-lessons.md`
