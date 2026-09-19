# Tier 3 · The UI system

Compose **foundation + ui + animation only**. No Material: nothing may bring in colour, ripples or
icons. Everything is built from a handful of primitives.

## Palette and primitives
`ui/theme/Theme.kt`: `FocusColors(bg, fg, dim, faint, line)`; `BlackTheme` (#000/#FFF/8E8E8E/
565656/2A2A2A) and `WhiteTheme` (inverted). Locals: `LocalFocusColors`, `LocalFocusFont`
(Default / Serif / Monospace), `LocalTextScale`. `applyFocusWindow()` = edge-to-edge, window
background, optional hidden status bar, and asks for the panel's fastest refresh mode.
`ui/components/Basics.kt`: `T()` is the only text primitive (theme colour, font, scale);
`Label`, `Hairline`, `FocusSwitch` (drawn), `FocusButton` (filled = inverted, or outlined),
`SettingRow`, `ToggleRow`, `TabChip(text, selected, onClick)` (text tab; shared by the review's
Today / Week and the drawer's Personal / Work; moved here from `ReviewScreen.kt`),
`Modifier.press`, `Modifier.monochrome()` + `hasColourGlyphs()`.
`Dialogs.kt`: `FocusDialog` (bordered panel; every popup is one), `MenuRow`, `ChoiceDialog`,
`MultiChoiceDialog`, `ConfirmDialog`, `TextInputDialog(numeric)`, `UnderlinedField`,
`AppPickerDialog(leading=…)`.

**Drawn pictograms** (the only ones): `WorkBadge` (briefcase, `Basics.kt`) and the music card's
play / pause / previous / next (`MediaGlyph`, `HomeWidgets.kt`). Canvas shapes in the text colour
with a `contentDescription`; never the Unicode characters, which many phones turn into colour
emoji.

## Touch feedback: it lights up (owner's requirement)
`PressIndication` (an `IndicationNodeFactory`, installed as `LocalIndication`) draws a rounded
rect of white at `0.17 × level` with **`BlendMode.Difference`** *behind* the content, so the same
code lightens black, darkens white and darkens the inverted primary button without knowing the
theme. Level animates 0→1 in 90 ms and back in 380 ms; on release it first lets the rise finish,
so the quickest tap is still visible. The glow is **exactly the element's bounds** (corner 12dp):
an earlier outset spilled over dialog borders and neighbouring buttons. Bare-text targets
therefore carry their own 12dp horizontal padding; the home column's side padding is 18dp so text
still sits 30dp from the edge. The animated value is read in the draw phase: no recomposition.

## Home layout that always fits (`HomeScreen`)
A home screen must never scroll or push the corner shortcuts off. `BoxWithConstraints` estimates
the height each arrangement needs from measured constants (dp, × text scale) and picks the
roomiest `Fit` (13 steps) that fits. Given up in this order: card proportions (square → flatter),
calendar/note lines, hosted-widget height, the ring (×0.88, then 132dp), cards as one-line strips,
smaller fast-app text, and last of all the cards themselves. **Pinned apps and the corner
shortcuts are never what gives** (a 5th app was once squeezed out by an under-estimate).
`heightOf` mirrors the composables row by row (`fixedGap()` is shared by layout and estimate; a
text line ≈ 1.2 × its size). Safety net: the bottom weighted `Spacer` reports its placed height;
0 means the fixed content overflowed, and `tighter` steps to the next fit. It only ever steps
down and is remembered on every input, so it cannot oscillate. Every section that is
switched on is part of the estimate. The sections are drawn in `Settings.homeOrder`; between them
sit weighted `Spacer`s, so the leftover height is shared out whatever the order and number
(screen time, when first, hugs the clock with a fixed 10dp instead). Below
172dp the ring switches to compact text ("85% charging", smaller type) so the bottom line still
fits the chord of the circle. Ring arc = battery % (or day fraction), `Animatable` from 0, 900 ms.
Battery via sticky `ACTION_BATTERY_CHANGED`, registered only while STARTED.

## Motion (all finite; idle draws 0 frames)
- Pager pages: `graphicsLayer { alpha = 1 − 1.2·distance; scale = 1 − 0.05·distance }` with
  `CompositingStrategy.ModulateAlpha` (no full-screen off-screen buffer per frame).
- Settings pages: `AnimatedContent` on `(stack.size, route)`, slide 1/7 width + fade,
  direction-aware. Review tabs: `Crossfade`. Wall: rises 28dp while fading in, 420 ms.
- **App launch animation.** Since Android 13 the system **ignores `makeCustomAnimation`
  (resource animations) for task-level opens**, which is what a launcher opening an app is. Only
  the built-in `ANIM_SCALE_UP` / `ANIM_CLIP_REVEAL` types are honoured. Clip-reveal from the tapped
  row was tried first: it hides most of the new app for half the animation, launches *felt* slow
  (worst from the ring, at the top), and the owner complained. Now `launchOptions()` =
  `makeScaleUpAnimation` from a rect inset 3% (the app covers the screen from its first frame);
  setting `LaunchAnimation.SYSTEM` passes no options. Not yet judged by the owner.

## Gestures on the home background
`detectVerticalDragGestures` (down = notifications via the service, else reflection on
`StatusBarManager.expandNotificationsPanel`; up = drawer with search focused) and
`detectTapGestures` (long-press = settings; double-tap = lock, if enabled). Callbacks go through
`rememberUpdatedState` because the detectors outlive recompositions. Children with `clickable`
consume their own taps, so long-press on a fast app opens its menu, on empty space the settings.
**Double tap to lock** (`Settings.doubleTapLock`, default `true` since 2026-09-19) needs the
accessibility service for `GLOBAL_ACTION_LOCK_SCREEN`; without it a toast, and the Gestures page
links to Setup.

## Swipe right on home = web search (`MainActivity` `Launcher`)
There is no page left of home, so the pager ignores that swipe. A `pointerInput` on the
`HorizontalPager` (keyed on `settings.swipeRightSearch`, default `true`) watches each gesture in
**`PointerEventPass.Initial` and consumes nothing**, so the pager and every child behave as
before. It only arms when `pager.currentPage == 0` and no scroll is in progress at touch-down,
and fires once per gesture when dx > 72dp and dx > 2·|dy|. `openWebSearch()` in `ui/Launching.kt`
tries, in order: `SearchManager.INTENT_ACTION_GLOBAL_SEARCH` targeted at the Google app package,
the same action untargeted, `ACTION_WEB_SEARCH`; toast "No search app found" if none resolves.
Launched with `launchOptions()` like any app. Deliberately not a hosted AppWidget: a widget would
put colour and icons on the home screen. Finger-left is still the drawer.

## Drawer details
The search field is composed even while the home page shows (pager keeps both pages), so it takes
`focusProperties { canFocus = isActive }` and the window is `stateAlwaysHidden`: otherwise it
grabs initial focus and pops the keyboard.
**When the drawer counts as open** (`drawerActive` in `MainActivity`, passed as `isActive`):
`if (pager is being dragged) settledPage == 1 else targetPage == 1`. It used to be
`currentPage == 1`, which flips halfway through the drag: with "Open the keyboard right away" on,
the keyboard popped up under a moving finger and dropped again if the finger turned back. Now it
rises when the swipe is let go towards the drawer (while the page glides in), falls when let go
towards home, and a swipe that returns to where it started changes nothing. Dragging the app list
clears focus and hides the keyboard (`listState.interactionSource`, not `isScrollInProgress`,
which the programmatic scroll-to-top on every keystroke would also trip). Search ranks: prefix, word prefix, contains, initials,
then a loose in-order match for ≥3 letters; labels are normalized once per list. The A–Z scrubber
consumes its own pointer events so the pager does not scroll.
- **Row under the search bar** (hidden while searching): Personal / Work `TabChip`s on the left,
  only when a visible app has `isWorkProfile`; "Sort: …" on the right, opening a `ChoiceDialog`.
  The tab choice is `remember`ed, not saved: the drawer opens on Personal. "Recent installs"
  follows the tab. **Search ignores tab and sort**: both profiles, ranked by match.
- **Sort** (`DrawerSort { ALPHA, MOST_USED, RECENT }`, `Settings.drawerSort`, default A–Z).
  Most used / Recent come from `UsageRepository.sortStats()` =
  `UsageStatsManager.queryAndAggregateUsageStats` over 7 days → package → (foreground ms,
  `lastTimeUsed`); empty without usage access, so the list stays A–Z. Deliberately the system's
  aggregates, not the `ForegroundTracker`: an order does not need exact minutes. Loaded on IO only
  while the drawer is the active page and the sort is not A–Z. `sortedByDescending` is stable, so
  ties keep A–Z order. **Work-profile entries count as zero** (usage inside a work profile is
  invisible), so the Work tab stays alphabetical. The list scrolls to the top when query, tab,
  sort or the stats change (a keyed list would otherwise follow the old top row).
- The A–Z scrubber and its letter index exist only for the A–Z sort.
- **Work marker = `WorkBadge`** (`Basics.kt`), the one pictogram in the app: a briefcase outline
  drawn on a `Canvas` in the theme's dim colour (two stroked round-rects and a line; no asset, no
  colour; `contentDescription` "Work profile"). After the label on drawer rows (14dp) and on
  pinned work apps on the home screen (0.62 × the fast-app text size; label
  `weight(1f, fill = false)`). It replaced the word "work" because the contributor asked for an
  icon twice. Pickers (`AppPickerDialog`) still say "work" as text.
