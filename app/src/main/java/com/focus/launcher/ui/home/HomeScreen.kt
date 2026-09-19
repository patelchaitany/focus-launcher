package com.focus.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableIntStateOf
import com.focus.launcher.data.HomeSection
import com.focus.launcher.ui.components.TextInputDialog
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.focus.launcher.Graph
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.CalEvent
import com.focus.launcher.data.CalendarInfo
import com.focus.launcher.data.CalendarRepository
import com.focus.launcher.data.ClockStyle
import com.focus.launcher.data.DayUsage
import com.focus.launcher.data.SHORTCUT_CAMERA
import com.focus.launcher.data.SHORTCUT_PHONE
import com.focus.launcher.data.Settings
import com.focus.launcher.data.TAP_ALARMS
import com.focus.launcher.data.TAP_BATTERY
import com.focus.launcher.data.TAP_CALENDAR
import com.focus.launcher.data.TAP_NOTHING
import com.focus.launcher.data.TAP_SCREEN_TIME
import com.focus.launcher.data.TimeFormat
import com.focus.launcher.service.FocusAccessibilityService
import com.focus.launcher.service.MediaListener
import com.focus.launcher.ui.components.AppPickerDialog
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.VSpace
import com.focus.launcher.ui.components.WorkBadge
import com.focus.launcher.ui.components.hasColourGlyphs
import com.focus.launcher.ui.components.monochrome
import com.focus.launcher.ui.components.press
import com.focus.launcher.ui.launchOptions
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.util.Perms
import java.time.LocalDate

/**
 * Page one of the launcher. Top to bottom: the clock; then, in the order chosen in settings, screen
 * time, the optional calendar (plain text), the music and note cards and up to five fast apps; the two
 * corner shortcuts stay at the bottom. Text only.
 *
 * Gestures on empty space: long-press opens settings, swipe down pulls the notification shade,
 * swipe up jumps to search, double-tap locks the phone. Swiping left (handled by the pager that
 * hosts this page) opens the app drawer.
 */
@Composable
fun HomeScreen(
    settings: Settings,
    apps: List<AppEntry>,
    today: DayUsage?,
    usageAccess: Boolean,
    setupIncomplete: Boolean,
    pendingReview: LocalDate?,
    resumeCount: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenSettings: (route: String?) -> Unit,
    onOpenReview: (week: LocalDate?) -> Unit,
) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val now by rememberNow()
    // The gesture detectors below outlive recompositions, so they must call the latest callbacks.
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)

    // Calendar section data
    var calendarAccess by remember { mutableStateOf(CalendarRepository.hasAccess(context)) }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarAccess = it }
    var shownCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    LaunchedEffect(settings.showCalendar, settings.calendarKey, resumeCount, calendarAccess) {
        if (settings.showCalendar) {
            calendarAccess = CalendarRepository.hasAccess(context)
            // Exactly one calendar is shown: the chosen one, else the main one with events coming up.
            val agenda = CalendarRepository.agenda(context, settings.calendarKey)
            shownCalendar = agenda.calendar
            events = agenda.events
        }
    }
    // The agenda is cached; the calendar provider tells us when that cache is no longer true.
    // The observer does no work itself, so a sync in the background costs nothing here.
    DisposableEffect(settings.showCalendar, calendarAccess) {
        if (!settings.showCalendar || !calendarAccess) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = CalendarRepository.invalidate()
        }
        val registered = try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            true
        } catch (_: Exception) {
            false
        }
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    var editingShortcut by remember { mutableStateOf<Boolean?>(null) } // true = left, false = right
    var editingNote by remember { mutableStateOf(false) }

    // Arranging in place: where each shown section sits in the column (top..bottom), which
    // sections are lifted right now, and where the finger holds their top edge.
    val bounds = remember { mutableStateMapOf<HomeSection, ClosedFloatingPointRange<Float>>() }
    var lifted by remember { mutableStateOf(emptyList<HomeSection>()) }
    var dragTop by remember { mutableFloatStateOf(0f) }

    val shown = remember(settings) { settings.homeOrder.filter(settings::shows) }
    val use24h = when (settings.timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
    }
    // One lookup per return home, shared by the card and the estimate of its height.
    val musicAccess = remember(settings.showMusic, resumeCount) { settings.showMusic && MediaListener.hasAccess(context) }
    var choosingClockTap by remember { mutableStateOf(false) }

    val favorites = remember(settings.favorites, apps) { settings.favorites.mapNotNull { key -> apps.firstOrNull { it.key == key } } }
    // Fast apps whose allowance for today is gone are shown dimmed.
    val spent = remember(favorites, today, settings.appLimits, settings.timersEnabled, settings.socialDefaultMin, settings.gameDefaultMin) {
        favorites.filter { app ->
            val limit = Graph.limits.limitFor(app.packageName, settings) ?: return@filter false
            (today?.perApp?.get(app.packageName) ?: 0L) >= limit.millis && !Graph.limits.hasFreePass(app.packageName)
        }.mapTo(HashSet()) { it.key }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(settings.swipeDownNotifications, settings.swipeUpSearch) {
                val threshold = 64.dp.toPx()
                var dragged = 0f
                var fired = false
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f; fired = false },
                    onVerticalDrag = { _, dy ->
                        dragged += dy
                        if (!fired && dragged > threshold && settings.swipeDownNotifications) {
                            fired = true
                            expandNotifications(context)
                        } else if (!fired && dragged < -threshold && settings.swipeUpSearch) {
                            fired = true
                            openDrawer(true)
                        }
                    },
                )
            }
            .pointerInput(settings.doubleTapLock) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        openSettings(null)
                    },
                    onDoubleTap = if (settings.doubleTapLock) {
                        {
                            if (!FocusAccessibilityService.lockScreen()) {
                                Toast.makeText(context, "Turn on the Focus timer service to lock with a double tap", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else null,
                )
            },
    ) {

        // A home screen must never scroll, push its corner shortcuts off the edge or lose a fast
        // app. Estimate the height each arrangement needs (constants measured on a real screen) and
        // take the roomiest one that fits, whatever the phone, text size, fast apps and enabled
        // sections. Detail is given up before the clock is: cards get flatter and shorter, then the
        // ring shrinks, then cards become one-line strips; the fast apps are never touched.
        val bars = WindowInsets.systemBars.asPaddingValues()
        val available = (maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()).value
        val textScale = LocalDensity.current.fontScale * settings.textScale
        val notices = (if (setupIncomplete) 1 else 0) + (if (pendingReview != null) 1 else 0)
        val eventCount = events.size
        val preferredRing = (maxHeight * if (settings.showCalendar) 0.25f else 0.29f).coerceIn(140.dp, 236.dp)
        // One of two cards side by side: the column's and the row's side padding and the gap come off.
        val pairWidth = (maxWidth.value - 36f - 12f - CARD_GAP) / 2

        // One grid for every card, Focus's own and other apps' widgets alike: two that follow each
        // other share a row, and every card row is the same height, so nothing on the page has
        // dimensions of its own. While something is being dragged it keeps to itself, so passing
        // another card does not glue the two together under the finger.
        val paired = remember(shown, lifted) {
            val out = ArrayList<List<HomeSection>>()
            for (section in shown) {
                val last = out.lastOrNull()
                val narrow = { it: HomeSection -> it in CARDS }
                val pairs = narrow(section) && last != null && last.size == 1 && narrow(last[0]) &&
                    (section in lifted) == (last[0] in lifted)
                if (pairs) out[out.lastIndex] = last!! + section else out += listOf(section)
            }
            out
        }
        val single = remember(shown) { shown.map { listOf(it) } }
        fun rowsOf(fit: Fit) = when (fit.cards) {
            Cards.FULL -> paired
            Cards.STRIPS -> single
            Cards.NONE -> single.filter { it[0] !in CARDS }
        }

        fun line(sp: Float) = sp * 1.2f * textScale
        val tileFrame = 28f + line(11f) + 8f // padding, title, gap
        /** The height of every card row: a square of half the width while there is room, then flatter, never under [MIN_CELL]. */
        fun cell(fit: Fit) = maxOf(if (fit.aspect > 0f) pairWidth / fit.aspect else 0f, MIN_CELL * textScale.coerceAtLeast(1f))
        /**
         * A card row is [cell] tall, unless what is in it needs more: the music card across the whole
         * width stacks title, artist and controls, and was cut off at the bottom of a low cell.
         */
        fun rowHeight(fit: Fit, row: List<HomeSection>) =
            if (row.size == 1 && row[0] == HomeSection.MUSIC) maxOf(cell(fit), tileFrame + line(17f) + line(13f) + 6f + line(17f) + 16f + (if (musicAccess) 0f else line(13f) + 8f))
            else cell(fit)

        /** What fits under a card's title at that height. */
        fun cellLines(fit: Fit, lineDp: Float) = ((cell(fit) - tileFrame) / (lineDp * textScale)).toInt().coerceAtLeast(1)

        fun heightOf(fit: Fit): Float {
            val hosted = { it: HomeSection -> it == HomeSection.NOTE && settings.noteWidget != 0 }
            val rows = rowsOf(fit)
            var total = 0f
            rows.forEachIndexed { index, row ->
                total += fixedGap(rows, index) ?: 0f
                val section = row[0]
                total += when {
                    section == HomeSection.SCREEN_TIME -> if (fit.compactTime) 12f + line(18f) else 12f + line(15f) + 3f + line(24f) + line(13f)
                    section == HomeSection.APPS ->
                        if (favorites.isEmpty()) 16f + 2 * 23f * textScale else favorites.size * (line(fit.textSp) + fit.padDp * 2)
                    // Title, then the week strip if wanted, then the events (or the one line that stands in for them).
                    section == HomeSection.CALENDAR -> 12f + line(11f) + 10f + (if (settings.showWeekStrip) 46f + line(11f) + 10f else 0f) +
                        if (calendarAccess && eventCount > 0) fit.lines.coerceAtMost(eventCount) * (line(14f) + 6f) else line(14f) + 8f
                    section in CARDS && fit.cards == Cards.FULL -> rowHeight(fit, row)
                    hosted(section) -> MIN_CELL // a widget cannot be a strip
                    else -> maxOf(STRIP_HEIGHT, line(15f) + 20f)
                }
            }
            val clock = if (settings.clockStyle == ClockStyle.RING) fit.ring.value else line(68f) + line(17f) + 8f
            val noticeLines = if (notices > 0) 14f + notices * (19f * textScale + 12f) else 0f
            val shortcuts = if (settings.showShortcuts) 28f + line(15f) else 20f
            return clock + noticeLines + total + shortcuts + 6f + 36f // 36 = air
        }

        val options = listOf(
            Fit(preferredRing, 26f, 10f, lines = 3, aspect = 1f),
            Fit(preferredRing, 24f, 8f, lines = 3, aspect = 1f),
            Fit(preferredRing, 24f, 8f, lines = 3, aspect = 1.3f),
            Fit(preferredRing, 24f, 7f, lines = 2, aspect = 1.3f),
            Fit(preferredRing, 24f, 7f, lines = 2, aspect = 1.7f),
            Fit(preferredRing, 22f, 6f, lines = 2, aspect = 0f),
            Fit(preferredRing, 22f, 6f, lines = 1, aspect = 0f),
            Fit(preferredRing * 0.88f, 22f, 6f, lines = 1, aspect = 0f),
            Fit(132.dp, 22f, 4f, lines = 1, aspect = 0f),
            // Screen time folds into one line before the cards give up their shape.
            Fit(132.dp, 22f, 4f, lines = 1, aspect = 0f, compactTime = true),
            Fit(132.dp, 22f, 4f, lines = 1, aspect = 0f, cards = Cards.STRIPS, compactTime = true),
            Fit(132.dp, 20f, 2f, lines = 1, aspect = 0f, cards = Cards.STRIPS, compactTime = true),
            // Never reached on a real phone: better no cards than a fast app that cannot be seen.
            Fit(132.dp, 20f, 2f, lines = 1, aspect = 0f, cards = Cards.NONE, compactTime = true),
        )
        // The estimate can be wrong (a font, a widget, a long title). The layout itself has the last
        // word: when it reports that nothing was left over, go one arrangement tighter. Only ever
        // tighter, and from the start again when what is on the screen changes, so it cannot flap.
        var tighter by remember(settings, favorites.size, eventCount, notices, maxWidth, maxHeight, textScale, lifted.isEmpty()) { mutableIntStateOf(0) }
        val estimated = options.indexOfFirst { heightOf(it) <= available }.let { if (it < 0) options.lastIndex else it }
        val fitIndex = (estimated + tighter).coerceAtMost(options.lastIndex)
        val fit = options[fitIndex]
        val rows = rowsOf(fit)
        val currentRows by rememberUpdatedState(rows)
        val ring = fit.ring.coerceAtLeast(132.dp)
        val favoriteSize = fit.textSp.sp
        val favoritePadding = fit.padDp.dp

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp)
                // Hold a section, then drag it up or down: the home screen is arranged in place.
                // (Fast apps keep their own long-press, the app menu; other sections move past them.)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val row = currentRows.firstOrNull { row -> bounds[row[0]]?.contains(down.position.y) == true }
                        if (row == null || HomeSection.APPS in row) return@awaitEachGesture
                        // One card of a pair is taken out on its own: that is how a pair is broken
                        // up and a card put beside a different one.
                        val grabbed = listOf(if (row.size == 2 && down.position.x > size.width / 2) row[1] else row[0])
                        // Over a section a long-press means "move this", not "open settings" behind it.
                        down.consume()
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragTop = bounds[grabbed[0]]?.start ?: return@awaitEachGesture
                        lifted = grabbed
                        try {
                            while (true) {
                                // On the way down (Initial), so the card underneath never takes the
                                // release for a tap.
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == held.id } ?: break
                                val released = !change.pressed
                                dragTop += change.positionChange().y
                                change.consume()
                                if (released) break
                                // Changes places with the row above or below once its middle has passed theirs.
                                val now = currentRows
                                val at = now.indexOfFirst { it[0] == grabbed[0] }
                                if (at < 0) continue // still drawn in its old pair for a frame
                                val mine = bounds[grabbed[0]] ?: continue
                                val middle = dragTop + (mine.endInclusive - mine.start) / 2
                                fun middleOf(row: List<HomeSection>?) = row?.let { bounds[it[0]] }?.let { (it.start + it.endInclusive) / 2 }
                                val above = now.getOrNull(at - 1)
                                val below = now.getOrNull(at + 1)
                                val aboveMiddle = middleOf(above)
                                val belowMiddle = middleOf(below)
                                if (above != null && aboveMiddle != null && middle < aboveMiddle) moveSections(grabbed, above[0], after = false)
                                else if (below != null && belowMiddle != null && middle > belowMiddle) moveSections(grabbed, below.last(), after = true)
                            }
                        } finally {
                            lifted = emptyList()
                        }
                    }
                },
            horizontalAlignment = settings.homeAlign.horizontal(),
        ) {
            Spacer(Modifier.weight(0.9f))

            HomeClock(
                settings = settings,
                now = now,
                ringSize = ring,
                onTap = { performClockTap(context, settings.clockTap, apps, onLaunch) { onOpenReview(null) } },
                onLongPress = { choosingClockTap = true },
                modifier = if (settings.clockStyle == ClockStyle.RING) Modifier.align(Alignment.CenterHorizontally) else Modifier,
            )

            // One-line notices. They disappear as soon as they have been dealt with.
            if (setupIncomplete || pendingReview != null) {
                VSpace(14.dp)
                if (pendingReview != null) {
                    Notice("Your weekly review is ready  →", strong = true) { onOpenReview(pendingReview) }
                }
                if (setupIncomplete) {
                    Notice("Finish setting up Focus  →", strong = false) { onOpenSettings("setup") }
                }
            }

            // Everything between the clock and the corners, in the order chosen in settings. Cards
            // that follow each other sit together; the weighted gaps elsewhere share out whatever
            // room is left, so the page breathes the same way with two sections as with six.
            val ringed = settings.clockStyle == ClockStyle.RING
            rows.forEachIndexed { index, row ->
                val section = row[0]
                val gap = fixedGap(rows, index)
                if (gap != null) VSpace(gap.dp) else Spacer(Modifier.weight(if (section == HomeSection.APPS) 0.9f else 0.6f))
                val isLifted = row[0] in lifted
                Column(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isLifted) 1f else 0f)
                        .onPlaced {
                            val top = it.positionInParent().y
                            val range = top..(top + it.size.height)
                            for (each in row) if (bounds[each] != range) bounds[each] = range
                        }
                        // Follows the finger: the difference between where it is held and where the
                        // layout has it right now. Read while drawing, so dragging recomposes nothing.
                        .graphicsLayer {
                            if (isLifted) {
                                translationY = dragTop - (bounds[row[0]]?.start ?: dragTop)
                                alpha = 0.85f
                            }
                        },
                    horizontalAlignment = if (section == HomeSection.SCREEN_TIME && ringed) Alignment.CenterHorizontally else settings.homeAlign.horizontal(),
                ) {
                    when (section) {
                        HomeSection.SCREEN_TIME -> ScreenTimeLine(
                            today = today,
                            hasAccess = usageAccess,
                            align = if (ringed) Alignment.CenterHorizontally else settings.homeAlign.horizontal(),
                            onClick = { if (usageAccess) onOpenReview(null) else Perms.openUsageAccess(context) },
                            compact = fit.compactTime,
                        )
                        HomeSection.CALENDAR -> CalendarWidget(
                            today = now.toLocalDate(),
                            mondayStart = settings.weekStartsMonday,
                            events = events,
                            hasAccess = calendarAccess,
                            use24h = use24h,
                            onClick = { openCalendarApp(context) },
                            onRequestAccess = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                            maxEvents = fit.lines,
                            calendarName = shownCalendar?.shortName,
                            showWeekStrip = settings.showWeekStrip,
                        )
                        HomeSection.MUSIC, HomeSection.NOTE -> {
                            val strips = fit.cards == Cards.STRIPS
                            val half = row.size == 2
                            val shape = if (strips) TileShape.STRIP else if (half) TileShape.SQUARE else TileShape.WIDE
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp).then(if (strips) Modifier else Modifier.height(rowHeight(fit, row).dp)),
                                horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
                            ) {
                                // Keyed by card: its state (a hosted widget's view, the player being
                                // followed) moves with it when cards change places.
                                for (card in row) key(card) {
                                    val size = Modifier.weight(1f).then(if (strips) Modifier else Modifier.fillMaxHeight())
                                    when {
                                        card == HomeSection.MUSIC -> MusicTile(resumeCount, musicAccess, shape, fit.roomy, size)
                                        settings.noteWidget != 0 -> HostedWidget(settings.noteWidget, if (strips) size.height(MIN_CELL.dp) else size)
                                        else -> NoteTile(settings.note, shape, maxLines = cellLines(fit, 20f), onClick = { editingNote = true }, modifier = size)
                                    }
                                }
                            }
                        }
                        HomeSection.APPS -> {
                            if (favorites.isEmpty()) {
                                T(
                                    "Swipe left for your apps.\nLong-press one to pin it here.",
                                    Modifier.fillMaxWidth().clickable { onOpenDrawer(false) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    size = 15.sp, color = c.dim, align = settings.homeAlign.text(), lineHeight = 23.sp,
                                )
                            } else {
                                for (app in favorites) {
                                    Row(
                                        Modifier
                                            .press(onLongClick = { onAppMenu(app) }) { onLaunch(app) }
                                            .padding(vertical = favoritePadding, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        T(
                                            app.label,
                                            Modifier.weight(1f, fill = false).then(if (hasColourGlyphs(app.label)) Modifier.monochrome() else Modifier),
                                            size = favoriteSize,
                                            color = if (app.key in spent) c.faint else c.fg,
                                            weight = FontWeight.Normal,
                                            maxLines = 1,
                                        )
                                        // Same marker as in the drawer, sized with the name next to it.
                                        if (app.isWorkProfile) WorkBadge(Modifier.padding(start = 10.dp), side = (favoriteSize.value * 0.62f).dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // The gaps get what the sections leave over. None left means a section further down was
            // cut short: the safety net described at `tighter`. Writes once per arrangement at most.
            Spacer(
                Modifier.weight(1f).onPlaced {
                    if (it.size.height == 0 && fitIndex < options.lastIndex) tighter = fitIndex - estimated + 1
                },
            )

            // Corner shortcuts
            if (settings.showShortcuts) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (left in listOf(true, false)) {
                        val spec = if (left) settings.leftShortcut else settings.rightShortcut
                        T(
                            shortcutLabel(spec, apps),
                            Modifier
                                .press(onLongClick = { editingShortcut = left }) { launchShortcut(context, spec, apps, onLaunch) }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            size = 15.sp, color = c.dim, maxLines = 1,
                        )
                    }
                }
            } else {
                VSpace(20.dp)
            }
            VSpace(6.dp)
        }
    }

    if (choosingClockTap) ClockTapDialog(settings, apps) { choosingClockTap = false }

    if (editingNote) {
        TextInputDialog(
            title = "Note",
            initial = settings.note,
            placeholder = "Write something",
            onDismiss = { editingNote = false },
            multiline = true,
        ) { text -> Graph.settings.update { it.copy(note = text) } }
    }

    editingShortcut?.let { left ->
        val visible = remember(apps, settings.hidden) { apps.filter { it.key !in settings.hidden } }
        AppPickerDialog(
            title = if (left) "Left shortcut" else "Right shortcut",
            subtitle = "Opens from the bottom corner of the home screen.",
            apps = visible,
            onDismiss = { editingShortcut = null },
            leading = listOf(
                "Phone" to { setShortcut(left, SHORTCUT_PHONE) },
                "Camera" to { setShortcut(left, SHORTCUT_CAMERA) },
            ),
            onPick = { setShortcut(left, it.key) },
        )
    }
}

@Composable
private fun Notice(text: String, strong: Boolean, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    T(
        text,
        Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        size = 14.sp,
        color = if (strong) c.fg else c.dim,
        weight = if (strong) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
    )
}

private fun setShortcut(left: Boolean, spec: String) {
    Graph.settings.update { if (left) it.copy(leftShortcut = spec) else it.copy(rightShortcut = spec) }
}

fun shortcutLabel(spec: String, apps: List<AppEntry>): String = when (spec) {
    SHORTCUT_PHONE -> "Phone"
    SHORTCUT_CAMERA -> "Camera"
    else -> apps.firstOrNull { it.key == spec }?.label ?: "Not set"
}

private fun launchShortcut(context: Context, spec: String, apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    when (spec) {
        SHORTCUT_PHONE -> Perms.start(context, Intent(Intent.ACTION_DIAL), options = launchOptions(context))
        SHORTCUT_CAMERA -> Perms.start(
            context,
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            options = launchOptions(context),
        )
        else -> apps.firstOrNull { it.key == spec }?.let(onLaunch)
    }
}

private val CARDS = setOf(HomeSection.MUSIC, HomeSection.NOTE)

/** The lowest a card row gets before cards turn into strips: enough for a title and two lines, or a widget that still says something. */
private const val MIN_CELL = 112f
private const val CARD_GAP = 10f

private enum class Cards { FULL, STRIPS, NONE }

/**
 * One way to lay the home screen out. [lines]: how many calendar events, and lines of a note on
 * its own, get room. [aspect]: width / height of two cards side by side, 1 = square, 0 = only as
 * tall as what is in them.
 */
private class Fit(
    val ring: Dp, val textSp: Float, val padDp: Float, val lines: Int, val aspect: Float,
    val cards: Cards = Cards.FULL,
    /** Screen time as one line instead of three. */
    val compactTime: Boolean = false,
) {
    /** Two lines of song title and the artist fit. */
    val roomy get() = aspect > 0f && aspect <= 1.3f
}

/** The fixed space above row [index], or null where a weighted gap shares out what is left. */
private fun fixedGap(rows: List<List<HomeSection>>, index: Int): Float? = when {
    // Screen time directly under the clock belongs to it.
    index == 0 && rows[0][0] == HomeSection.SCREEN_TIME -> 10f
    // Cards that follow each other form one block.
    index > 0 && rows[index][0] in CARDS && rows[index - 1][0] in CARDS -> CARD_GAP
    else -> null
}

/** Takes [unit] out of the home order and puts it back right before or after [anchor]. */
private fun moveSections(unit: List<HomeSection>, anchor: HomeSection, after: Boolean) = Graph.settings.update { settings ->
    val rest = settings.homeOrder - unit.toSet()
    val at = rest.indexOf(anchor) + if (after) 1 else 0
    settings.copy(homeOrder = rest.take(at) + unit + rest.drop(at))
}

private fun openAlarms(context: Context) {
    Perms.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), options = launchOptions(context))
}

private fun openCalendarApp(context: Context) {
    Perms.start(
        context,
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        // Calendars that do not register the category still open on "show me this moment".
        Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri()),
        options = launchOptions(context),
    )
}

/** Name of a clock tap action, for settings. */
fun clockTapLabel(spec: String, apps: List<AppEntry>): String = when (spec) {
    TAP_ALARMS -> "Alarms"
    TAP_CALENDAR -> "Calendar"
    TAP_SCREEN_TIME -> "Screen time"
    TAP_BATTERY -> "Battery"
    TAP_NOTHING -> "Nothing"
    else -> apps.firstOrNull { it.key == spec }?.label ?: "Not set"
}

private fun performClockTap(
    context: Context,
    spec: String,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onOpenScreenTime: () -> Unit,
) {
    when (spec) {
        TAP_ALARMS -> openAlarms(context)
        TAP_CALENDAR -> openCalendarApp(context)
        TAP_SCREEN_TIME -> onOpenScreenTime()
        TAP_BATTERY -> Perms.start(
            context,
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
            options = launchOptions(context),
        )
        TAP_NOTHING -> Unit
        else -> apps.firstOrNull { it.key == spec }?.let(onLaunch)
    }
}

/** Pulls the notification shade down: via the timer service when it is on, else the status-bar service. */
@SuppressLint("WrongConstant", "PrivateApi")
private fun expandNotifications(context: Context) {
    if (FocusAccessibilityService.openNotifications()) return
    try {
        val statusBar = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
    } catch (_: Exception) {
    }
}
