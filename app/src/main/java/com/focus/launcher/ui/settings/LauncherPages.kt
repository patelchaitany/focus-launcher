package com.focus.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.Graph
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.ClockStyle
import com.focus.launcher.data.FontChoice
import com.focus.launcher.data.HomeAlign
import com.focus.launcher.data.LaunchAnimation
import com.focus.launcher.data.MAX_FAVORITES
import com.focus.launcher.data.RingMode
import com.focus.launcher.data.SHORTCUT_CAMERA
import com.focus.launcher.data.SHORTCUT_PHONE
import com.focus.launcher.data.Settings
import com.focus.launcher.data.TimeFormat
import com.focus.launcher.ui.components.AppPickerDialog
import com.focus.launcher.ui.components.ChoiceDialog
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.MenuRow
import com.focus.launcher.ui.components.SettingRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.ToggleRow
import com.focus.launcher.ui.home.ClockTapDialog
import com.focus.launcher.ui.home.clockTapLabel
import com.focus.launcher.ui.home.shortcutLabel
import com.focus.launcher.ui.launchApp
import com.focus.launcher.ui.theme.LocalFocusColors

private fun update(transform: (Settings) -> Settings) = Graph.settings.update(transform)

// ---- Home screen -----------------------------------------------------------------------------

private enum class HomeDialog { NONE, CLOCK, RING, TAP, TIME_FORMAT, ALIGN, LEFT, RIGHT }

@Composable
internal fun HomePage(settings: Settings, apps: List<AppEntry>, onBack: () -> Unit, go: (String) -> Unit) {
    var dialog by remember { mutableStateOf(HomeDialog.NONE) }
    val close = { dialog = HomeDialog.NONE }
    val favoriteCount = settings.favorites.count { key -> apps.any { it.key == key } }

    Page("Home screen", onBack) {
        Section("Clock")
        SettingRow("Style", value = settings.clockStyle.label, onClick = { dialog = HomeDialog.CLOCK })
        SettingRow(
            "Ring shows",
            subtitle = if (settings.ringMode == RingMode.BATTERY) "A full circle is a full battery." else "The circle fills up as the day passes.",
            value = settings.ringMode.label,
            onClick = { dialog = HomeDialog.RING },
        )
        SettingRow("Tap on the clock", subtitle = "Opens an app of your choice, or alarms, calendar, screen time. Long-pressing the clock gets you here too.", value = clockTapLabel(settings.clockTap, apps), onClick = { dialog = HomeDialog.TAP })
        SettingRow("Time format", value = settings.timeFormat.label, onClick = { dialog = HomeDialog.TIME_FORMAT })
        ToggleRow("Show the date", settings.showDate) { v -> update { it.copy(showDate = v) } }

        Section("Sections")
        SettingRow(
            "Arrange home screen",
            subtitle = "Drag the sections into your order. Calendar, next alarm, music controls and a note are switched on and set up here.",
            onClick = { go(Routes.ARRANGE) },
        )

        Section("Fast apps")
        SettingRow("Fast apps", subtitle = "Up to $MAX_FAVORITES apps, one tap from the home screen.", value = "$favoriteCount / $MAX_FAVORITES", onClick = { go(Routes.FAST_APPS) })
        SettingRow("Alignment", value = settings.homeAlign.label, onClick = { dialog = HomeDialog.ALIGN })

        Section("Corner shortcuts")
        ToggleRow("Show shortcuts", settings.showShortcuts) { v -> update { it.copy(showShortcuts = v) } }
        SettingRow("Bottom left", value = shortcutLabel(settings.leftShortcut, apps), enabled = settings.showShortcuts, onClick = { dialog = HomeDialog.LEFT })
        SettingRow("Bottom right", value = shortcutLabel(settings.rightShortcut, apps), enabled = settings.showShortcuts, onClick = { dialog = HomeDialog.RIGHT })
    }

    when (dialog) {
        HomeDialog.NONE -> Unit
        HomeDialog.CLOCK -> ChoiceDialog("Clock style", ClockStyle.entries.map { it to it.label }, settings.clockStyle, close) { v -> update { it.copy(clockStyle = v) } }
        HomeDialog.RING -> ChoiceDialog("Ring shows", RingMode.entries.map { it to it.label }, settings.ringMode, close) { v -> update { it.copy(ringMode = v) } }
        HomeDialog.TAP -> ClockTapDialog(settings, apps, close)
        HomeDialog.TIME_FORMAT -> ChoiceDialog("Time format", TimeFormat.entries.map { it to it.label }, settings.timeFormat, close) { v -> update { it.copy(timeFormat = v) } }
        HomeDialog.ALIGN -> ChoiceDialog("Alignment", HomeAlign.entries.map { it to it.label }, settings.homeAlign, close) { v -> update { it.copy(homeAlign = v) } }
        HomeDialog.LEFT, HomeDialog.RIGHT -> {
            val left = dialog == HomeDialog.LEFT
            val set: (String) -> Unit = { spec -> update { if (left) it.copy(leftShortcut = spec) else it.copy(rightShortcut = spec) } }
            AppPickerDialog(
                title = if (left) "Bottom left shortcut" else "Bottom right shortcut",
                apps = apps.filter { it.key !in settings.hidden },
                onDismiss = close,
                leading = listOf("Phone" to { set(SHORTCUT_PHONE) }, "Camera" to { set(SHORTCUT_CAMERA) }),
                onPick = { set(it.key) },
            )
        }
    }
}

@Composable
internal fun FastAppsPage(settings: Settings, apps: List<AppEntry>, onBack: () -> Unit) {
    val c = LocalFocusColors.current
    var adding by remember { mutableStateOf(false) }
    val favorites = settings.favorites.mapNotNull { key -> apps.firstOrNull { it.key == key } }
    val keys = favorites.map { it.key }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in keys.indices) return
        val next = keys.toMutableList()
        next.add(target, next.removeAt(index))
        update { it.copy(favorites = next) }
    }

    Page("Fast apps", onBack) {
        Note("The apps listed on your home screen, in this order. You can also add one from the app drawer: long-press it, then “Move to fast apps”.")
        favorites.forEachIndexed { i, app ->
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(app.label, Modifier.weight(1f).padding(vertical = 15.dp), size = 18.sp, maxLines = 1)
                T("↑", Modifier.clickable(enabled = i > 0) { move(i, -1) }.padding(12.dp), size = 18.sp, color = if (i > 0) c.fg else c.line)
                T("↓", Modifier.clickable(enabled = i < keys.lastIndex) { move(i, 1) }.padding(12.dp), size = 18.sp, color = if (i < keys.lastIndex) c.fg else c.line)
                T("Remove", Modifier.clickable { update { it.copy(favorites = keys - app.key) } }.padding(12.dp), size = 14.sp, color = c.dim)
            }
        }
        if (favorites.size < MAX_FAVORITES) {
            SettingRow("Add an app", value = "${favorites.size} / $MAX_FAVORITES", onClick = { adding = true })
        } else {
            Note("That's the maximum. Five is plenty.")
        }
    }

    if (adding) {
        AppPickerDialog(
            title = "Add a fast app",
            apps = apps.filter { it.key !in keys && it.key !in settings.hidden },
            onDismiss = { adding = false },
            onPick = { app -> update { it.copy(favorites = (keys + app.key).take(MAX_FAVORITES)) } },
        )
    }
}

// ---- App drawer ------------------------------------------------------------------------------

@Composable
internal fun DrawerPage(settings: Settings, onBack: () -> Unit, go: (String) -> Unit) {
    Page("App drawer", onBack) {
        Section("Search")
        ToggleRow("Open the keyboard right away", settings.autoKeyboard, subtitle = "Start typing the moment you swipe to the drawer.") { v -> update { it.copy(autoKeyboard = v) } }
        ToggleRow("Open when one app is left", settings.autoLaunch, subtitle = "Launches the app as soon as your search matches only one.") { v -> update { it.copy(autoLaunch = v) } }

        Section("List")
        ToggleRow("Recently installed", settings.showRecentInstalls, subtitle = "Apps installed in the last 24 hours, at the top.") { v -> update { it.copy(showRecentInstalls = v) } }
        ToggleRow("Show timer progress", settings.showUsageInDrawer, subtitle = "“12m / 30m” next to every app that has a daily limit.") { v -> update { it.copy(showUsageInDrawer = v) } }
        SettingRow("Hidden apps", value = settings.hidden.size.toString(), onClick = { go(Routes.HIDDEN) })
    }
}

@Composable
internal fun HiddenAppsPage(settings: Settings, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    val hidden = apps.filter { it.key in settings.hidden }

    Page("Hidden apps", onBack) {
        if (hidden.isEmpty()) {
            Note("Nothing is hidden. To hide an app, long-press it in the drawer and choose “Hide app”. Hidden apps stay installed and can be opened from here.")
        }
        for (app in hidden) SettingRow(app.label, onClick = { selected = app })
    }

    selected?.let { app ->
        FocusDialog({ selected = null }, title = app.label) {
            MenuRow("Open") {
                launchApp(context, scope, app)
                selected = null
            }
            MenuRow("Unhide") {
                update { it.copy(hidden = it.hidden - app.key) }
                selected = null
            }
        }
    }
}

// ---- Appearance ------------------------------------------------------------------------------

private enum class LookDialog { NONE, THEME, FONT, SIZE, LAUNCH }

private val TEXT_SIZES = listOf(0.9f to "Small", 1f to "Default", 1.1f to "Large", 1.2f to "Larger")

@Composable
internal fun AppearancePage(settings: Settings, onBack: () -> Unit) {
    var dialog by remember { mutableStateOf(LookDialog.NONE) }
    val close = { dialog = LookDialog.NONE }

    Page("Appearance", onBack) {
        Section("Black and white")
        SettingRow("Theme", subtitle = "Pure black saves battery on OLED screens.", value = if (settings.dark) "Black" else "White", onClick = { dialog = LookDialog.THEME })
        SettingRow("Typeface", value = settings.font.label, onClick = { dialog = LookDialog.FONT })
        SettingRow("Text size", value = TEXT_SIZES.firstOrNull { it.first == settings.textScale }?.second ?: "Default", onClick = { dialog = LookDialog.SIZE })

        Section("Screen")
        SettingRow(
            "Opening apps",
            subtitle = "Fast puts the app over the whole screen from its first frame. System default uses your phone's own animation.",
            value = settings.launchAnimation.label,
            onClick = { dialog = LookDialog.LAUNCH },
        )
        ToggleRow("Hide the status bar", settings.hideStatusBar, subtitle = "No notification icons on the home screen. Swipe down from the top edge to peek.") { v -> update { it.copy(hideStatusBar = v) } }
    }

    when (dialog) {
        LookDialog.NONE -> Unit
        LookDialog.THEME -> ChoiceDialog("Theme", listOf(true to "Black", false to "White"), settings.dark, close) { v -> update { it.copy(dark = v) } }
        LookDialog.FONT -> ChoiceDialog("Typeface", FontChoice.entries.map { it to it.label }, settings.font, close) { v -> update { it.copy(font = v) } }
        LookDialog.LAUNCH -> ChoiceDialog("Opening apps", LaunchAnimation.entries.map { it to it.label }, settings.launchAnimation, close) { v -> update { it.copy(launchAnimation = v) } }
        LookDialog.SIZE -> ChoiceDialog("Text size", TEXT_SIZES, settings.textScale, close) { v -> update { it.copy(textScale = v) } }
    }
}

// ---- Gestures --------------------------------------------------------------------------------

@Composable
internal fun GesturesPage(settings: Settings, status: SetupStatus, onBack: () -> Unit, go: (String) -> Unit) {
    Page("Gestures", onBack) {
        Note("Swiping left always opens the app drawer, and a long-press on empty space opens these settings.")
        ToggleRow("Swipe down for notifications", settings.swipeDownNotifications) { v -> update { it.copy(swipeDownNotifications = v) } }
        ToggleRow("Swipe up to search", settings.swipeUpSearch, subtitle = "Jumps to the drawer with the keyboard open.") { v -> update { it.copy(swipeUpSearch = v) } }
        // Also under App drawer. It is looked for here too: it is what swiping to the drawer does.
        ToggleRow("Keyboard opens with the drawer", settings.autoKeyboard, subtitle = "Start typing the moment you swipe to your apps.") { v -> update { it.copy(autoKeyboard = v) } }
        ToggleRow("Swipe right for web search", settings.swipeRightSearch, subtitle = "Opens the Google search box, like the page left of a stock home screen.") { v -> update { it.copy(swipeRightSearch = v) } }
        ToggleRow("Double tap to lock", settings.doubleTapLock, subtitle = "Turns the screen off. Uses the Focus timer service.") { v -> update { it.copy(doubleTapLock = v) } }
        if (settings.doubleTapLock && !status.timerService) {
            Note("The Focus timer service is off, so double tap cannot lock yet.  Open setup  →") { go(Routes.SETUP) }
        }
    }
}
