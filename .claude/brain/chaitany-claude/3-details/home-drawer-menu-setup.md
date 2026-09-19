# Tier 3 · Home, drawer, the long-press menu, and Setup

Code: `ui/home/HomeScreen.kt`, `ui/home/HomeWidgets.kt`, `ui/drawer/DrawerScreen.kt`,
`ui/drawer/AppMenu.kt`, `ui/settings/SetupPage.kt`, `ui/settings/LauncherPages.kt`,
`data/Settings.kt`. Layout fitting, touch feedback and motion are in `ui-system.md`.

## Home (page 0), top to bottom, after the owner's sketch
1. **Clock**: ring (`ClockStyle.RING`, arc = battery % or day passed) or plain. Tap = `clockTap`
   (alarms by default, or calendar / screen time / battery / nothing / any app); long-press opens
   the chooser (`ClockTapDialog`).
2. **Screen time** (`ScreenTimeLine`, always there, no setting): "Screen Time", today's total at
   24sp, "N% of today" (of 24 h). Tapping it opens the review, or the usage-access switch if that
   is still missing. The 24-hour bar that used to be a home section now lives only in the review
   (a contributor's change; open decision 14 in `2-overview/user-and-decisions.md`).
3. **Notices**, only when needed: "Finish setting up Focus →" and "Your weekly review is ready →".
   They sit directly under the clock, above every section.
4. **Optional sections**, each off by default.
   - **Calendar**: the agenda as plain text, exactly as the owner built it (small-caps title, the
     calendar's name, optional week strip, event rows; `calendar.md`). It was a card and could
     host a calendar app's widget for a day; the contributor asked for "what we had previously"
     back, so it is neither now, and `Settings.calendarWidget` is gone.
   Music and the note are **cards** (`Tile` in `HomeWidgets.kt`: 18dp rounded panel filled with
   `line` at 60%, small-caps title, content below; no icons). A **next-alarm card** existed for a
   day too and was removed at the same request (`HomeSection.ALARM`, `AlarmTile`, `showAlarm`: all
   gone; a saved order that still names it is read without it).
   - **Music** (`MusicTile`, full width): song title, artist, "Prev · Play/Pause · Next"; a tap on
     the card opens the player. With **notification access** (`service/MediaListener.kt`, an empty
     `NotificationListenerService` that reads nothing: it is only the key Android demands for
     `MediaSessionManager.getActiveSessions`) the card follows the active `MediaController` by
     callback, registered in a `LifecycleStartEffect` so nothing listens while home is hidden.
     The listener calls `requestUnbind()` in `onListenerConnected`: the *grant* is what the API
     checks, while a bound listener, even an empty one, is handed every notification on the phone
     around the clock. `NowPlaying` is a data class fed from the callback's own arguments, so a
     player ticking its position every second changes no state and draws no frame.
     Without it the three controls still work as media keys (`AudioManager.dispatchMediaKeyEvent`)
     and one line offers the access; the song's name cannot be had any other way;
   - **Note** (`NoteTile`): `Settings.note`, at least two and up to `Fit.lines` lines, tap =
     multi-line `TextInputDialog`.
   **Another app's widget** can take the note's place
   (`Settings.noteWidget` = appWidgetId, 0 = Focus's own card;
   `ui/home/HostedWidget.kt`: one `AppWidgetHost`, listening only between `MainActivity.onStart`
   and `onStop` and only if an id is set; providers of every profile via
   `getInstalledProvidersForProfile`; bind directly or through `ACTION_APPWIDGET_BIND`). Drawn by
   the other app, so not text-only: Focus rounds it, gives it the same cell as any card (its real size, from `BoxWithConstraints`, is
   reported with `updateAppWidgetSize` whenever it changes) and
   removes the colour with a saturation-0 hardware layer (view created with the application
   context: the host outlives any Activity). Work-profile widgets are offered wherever the
   organisation lists the app as a cross-profile widget provider (`dumpsys device_policy` →
   `crossProfileWidgetProviders`); that would also be the only sanctioned way to see a work
   calendar, should a calendar widget slot ever come back. Once per process `WidgetHost` releases
   every bound id that no setting points to. A widget's configure activity is
   started after binding (result not awaited). The widget sits on the same panel as Focus's own
   cards (same outer edge whatever margins it draws inside) and is told its real size through
   `updateAppWidgetSize` every time the fit changes its height: without that a widget lays itself
   out for a size it does not have and is cut off mid-content. Never below 110dp before screen
   time has folded into one line (`Fit.compactTime`); 80dp only together with strips.
   **One grid** (`TileShape { SQUARE, WIDE, STRIP }`): every card, Focus's own and hosted
   widgets alike, is a cell of the same grid. Two cards that follow each other in the order share
   a row; one without a neighbour spans the width; **every card row has the same height**,
   `cell(fit)` = half the width divided by `Fit.aspect` (1.0 → 1.3 → …), never under `MIN_CELL`
   (112dp). What a card shows follows from the cell (`cellLines`). One exception, found on a
   screenshot: the music card across the full width needs more than a low cell and gets it
   (`rowHeight`), or its controls are cut off. An earlier version gave alarm/music
   one set of dimensions and widgets/calendar another, and was rejected as "bad bad bad": no card
   may have dimensions of its own. When room runs out every card collapses to a one-line `Strip`
   (a hosted widget cannot, it keeps `Fit.widget`), and as the very last resort the cards go.
   Card rows that follow each other sit 10dp apart as one block; weighted gaps separate the block
   from everything else.
   **Order:** `Settings.homeOrder: List<HomeSection>` (SCREEN_TIME, CALENDAR, ALARM, MUSIC, NOTE,
   APPS) holds every section, shown or not; `Settings.shows()` says which are on (screen time and
   fast apps always). `fromJson` appends sections a saved order has never heard of. Arranged in
   Settings → Home screen → **Arrange home screen** (`ArrangePage`, route `arrange`): hold a row and
   drag to move it (`detectDragGesturesAfterLongPress`; the row swaps once it has travelled a row's
   height, rows are `key`ed so the drag survives the reorder), tap to show or hide. Only the four cards are listed: screen time and the fast
   apps are always on (asked for, then reversed once: no switches for them) and are moved on
   the home screen only; a move in the list re-inserts the card next to the card it passed, so
   unlisted sections keep their place. Sub-lines appear only under a section that is on:
   "Shows: … · change" under calendar and note opens the widget picker (relevant widgets only:
   calendar apps by `CATEGORY_APP_CALENDAR` or name, note/to-do apps by name; "All widgets…" and
   Search reach the rest; list built on IO); under the calendar also which calendar
   (`CalendarPickerDialog`, with the work-profile note) and the week strip. **Arrange is the only
   place for calendar settings** (owner of the phone: "two times settings of calendar … remove the
   outside one"); `HomePage` no longer has any.
   **On the home screen itself:** hold a section, then drag it up or down. One `pointerInput` on
   the home column: the down is matched to a row by the recorded bounds
   (`onGloballyPositioned`), `awaitLongPressOrCancellation`, then the drag is read in the
   *Initial* pass and consumed (so the card underneath never takes the release for a tap, and the
   pager does not scroll). The lifted row is drawn at `dragTop - layoutTop` in `graphicsLayer`, so
   it stays under the finger however the layout reflows; it changes places when its middle passes
   the neighbour's middle (`moveSections`). What is lifted is the single card under the finger (left or right half of a pair), which
   is how a pair is split and a card put beside another. A lifted card does not pair with a resting one. Fast
   apps cannot be grabbed (their long-press is the app menu) but others move past them; over a
   section the down is consumed so the background's long-press (settings) stays out of it.
5. **Fast apps**: at most `MAX_FAVORITES = 5`, text only, aligned by `homeAlign`
   (left / center / right). Long-press = the app menu.
6. **Corner shortcuts**: `leftShortcut` / `rightShortcut`. Defaults `auto:phone` → `ACTION_DIAL`
   and `auto:camera` → `INTENT_ACTION_STILL_IMAGE_CAMERA`, so they work whatever dialer or camera
   is installed; either can be any app. Long-press a corner to change it in place;
   `showShortcuts` hides both.
Background gestures (each a setting): swipe down = notifications, swipe up = drawer with search
focused, double-tap = lock (on by default, needs the service), long-press = settings, swipe right
(finger moves right; nothing is to the left of home) = the phone's web search (`ui-system.md`).

## Drawer (page 1, swipe left)
- Search field on top. `autoKeyboard` opens the keyboard on arrival (off by default; when the
  drawer counts as "arrived at" is in `ui-system.md`; dragging the list hides it); `autoLaunch`
  opens the app when ≥ 2 letters leave exactly one result.
- **"Installed in the last 24 hours"**: `firstInstallTime` within 24 h, newest first, Focus itself
  excluded; hidden while searching; `showRecentInstalls` turns it off.
- Under the search field, hidden while searching: Personal / Work tabs (only when a work profile
  has launchable apps; the list and "installed in the last 24 hours" follow the tab, search
  covers both) and "Sort: A–Z / Most used / Recent" (`Settings.drawerSort`; the two usage orders
  come from `UsageRepository.sortStats()`, 7 days).
- Then every visible app in that order; the scrubber only for A–Z. `showUsageInDrawer` adds
  today's time next to apps that have a limit.
- Hidden apps are filtered out of the list and the search; they are managed in
  Settings → Drawer → Hidden apps. Search matches the name shown in Focus (so a renamed app is
  found by its new name, not its system name).
- Work-profile apps sit under the Work tab, carry the drawn briefcase (`WorkBadge`, also on pinned
  apps on the home screen) and are launched through `LauncherApps.startMainActivity` with their user.

## The long-press menu (`AppMenu`): the order is the owner's, do not reorder
Title = the app's name; the subtitle shows the system name if renamed, today's time and the limit.
1. **Uninstall**: `ACTION_DELETE package:…` (+ `EXTRA_USER` for work-profile apps); not offered
   for system apps. The system asks for confirmation; Focus never removes anything itself.
2. **App info**: `LauncherApps.startAppDetailsActivity`.
3. **Move to fast apps** / Remove from fast apps, with "n / 5". When full: "Fast apps are full",
   choose the one to replace.
4. **App timer**: only if `canLimit`. Opens `TimerDialog`: the category default, No limit,
   presets, Custom…; the subtitle says how Focus classified the app (`describeCategory`).
5. **Rename**: inside Focus only; empty restores the system label.
6. **Hide app** / Unhide app.

## Setup page (Settings → Setup; also the "finish setup" notice)
"Three switches make Focus work", each row opens the right system screen and shows its state on
return:
1. **Default home**: `RoleManager.ROLE_HOME` request on Android 10+, else the home settings.
2. **Usage access**: screen time, timers and the review depend on it.
3. **App locking**: the accessibility service. Sideloaded apps hit Android's "restricted setting"
   block, so the row explains the way through (App info → ⋮ → Allow restricted settings).
Optional: **Notifications** (used only for the weekly review) and **Calendar section** (requests
`READ_CALENDAR` and turns the section on in one step).
Focus never flips these switches itself, and neither does an agent over adb
(`2-overview/device-testing.md`).

## Settings, in short
One immutable `Settings` data class stored as JSON; `fromJson` tolerates missing keys. Pages:
main · setup · home · fastapps · drawer · hidden · timers · timerapps · weekly · appearance ·
gestures · about. Open one directly: `am start -n com.focus.launcher/.SettingsActivity --es route
<name>`. Appearance: dark or light, font (sans / serif / mono), text scale, hidden status bar,
launch animation (fast / system).
