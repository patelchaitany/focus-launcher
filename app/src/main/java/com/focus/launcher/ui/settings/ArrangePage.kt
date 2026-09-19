package com.focus.launcher.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focus.launcher.Graph
import com.focus.launcher.data.CALENDAR_ALL
import com.focus.launcher.data.CalendarInfo
import com.focus.launcher.data.CalendarRepository
import com.focus.launcher.data.HomeSection
import com.focus.launcher.data.Settings
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.FocusSwitch
import com.focus.launcher.ui.components.MenuRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.UnderlinedField
import com.focus.launcher.ui.components.press
import com.focus.launcher.ui.home.WidgetChoice
import com.focus.launcher.ui.home.WidgetHost
import com.focus.launcher.ui.theme.LocalFocusColors
import kotlin.math.roundToInt

private val ROW = 56.dp
private val LISTED = setOf(HomeSection.CALENDAR, HomeSection.MUSIC, HomeSection.NOTE)

/**
 * The home screen's sections as a list: hold a row and drag it to move that section, tap it to
 * show or hide it. Arranging a list is far simpler, and far harder to get wrong, than dragging
 * things around on the home screen itself, which would then need an "edit mode" of its own.
 */
@Composable
internal fun ArrangePage(settings: Settings, onBack: () -> Unit) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var calendarGrants by remember { mutableIntStateOf(0) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarGrants++ }
    // Focus's own calendar card shows one calendar; null until the list has been read.
    val ownCalendar = settings.showCalendar
    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    var shownCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    var calendarCounts by remember { mutableStateOf(emptyMap<String, Int>()) }
    LaunchedEffect(ownCalendar, settings.calendarKey, calendarGrants) {
        if (!ownCalendar) return@LaunchedEffect
        val found = CalendarRepository.calendars(context)
        calendarCounts = CalendarRepository.upcomingCounts(context, found)
        shownCalendar = CalendarRepository.choose(context, settings.calendarKey, found)
        calendars = found
    }
    // A Work profile exists, yet none of its calendars came through: the organisation says no.
    val workBlocked = remember(calendars) { calendars?.none { it.work } == true && CalendarRepository.hasWorkProfile(context) }
    var pickingCalendar by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<HomeSection?>(null) }
    // A widget the system wants the user to approve first: section and reserved id, until the answer.
    var awaiting by remember { mutableStateOf<Pair<HomeSection, Int>?>(null) }
    fun use(section: HomeSection, id: Int) {
        val old = Graph.settings.value.noteWidget
        Graph.settings.update { it.copy(noteWidget = id, showNote = true) }
        if (old != id) WidgetHost.release(context, old) // a system call: not inside the settings lock
        if (id != 0) (context as? Activity)?.let { WidgetHost.configure(it, id) }
    }
    val askBind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        awaiting?.let { (section, id) -> if (result.resultCode == Activity.RESULT_OK) use(section, id) else WidgetHost.release(context, id) }
        awaiting = null
    }
    var dragged by remember { mutableStateOf<HomeSection?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    fun toggle(section: HomeSection) = Graph.settings.update {
        when (section) {
            HomeSection.CALENDAR -> it.copy(showCalendar = !it.showCalendar)
            HomeSection.MUSIC -> it.copy(showMusic = !it.showMusic)
            HomeSection.NOTE -> it.copy(showNote = !it.showNote)
            HomeSection.SCREEN_TIME, HomeSection.APPS -> it
        }
    }

    Page("Arrange home screen", onBack) {
        Note("Hold a row and drag it to change the order; tap a row to show or hide it. Music and the note are cards and sit side by side when one follows the other. On the home screen itself, hold a section and drag it up or down.")
        // Only what can be switched off is listed. Screen time and the fast apps are always on; where they sit is
        // changed on the home screen itself, by holding and dragging.
        for (section in settings.homeOrder.filter { it in LISTED }) {
            // Keyed, so a row keeps its running drag while the list is reordered under it.
            key(section) {
                val lifted = dragged == section
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW)
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset else 0f }
                        .then(if (lifted) Modifier.background(c.line) else Modifier)
                        .pointerInput(section) {
                            val rowPx = ROW.toPx()
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragged = section
                                    offset = 0f
                                },
                                onDragEnd = { dragged = null },
                                onDragCancel = { dragged = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    // Once the row has travelled a whole row's height, it changes
                                    // places; what is left of the travel carries on from there.
                                    val listed = Graph.settings.value.homeOrder.filter { it in LISTED }
                                    val from = listed.indexOf(section)
                                    val to = (from + (offset / rowPx).roundToInt()).coerceIn(0, listed.lastIndex)
                                    if (to != from) {
                                        // Placed next to the card it passed; whatever is not listed here stays where it is.
                                        val passed = listed[to]
                                        Graph.settings.update {
                                            val rest = it.homeOrder - section
                                            val at = rest.indexOf(passed) + if (to > from) 1 else 0
                                            it.copy(homeOrder = rest.take(at) + section + rest.drop(at))
                                        }
                                        offset -= (to - from) * rowPx
                                    }
                                },
                            )
                        }
                        .press {
                            val turningOn = !settings.shows(section)
                            toggle(section)
                            if (section == HomeSection.CALENDAR && turningOn && !CalendarRepository.hasAccess(context)) {
                                askCalendar.launch(Manifest.permission.READ_CALENDAR)
                            }
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(section.label, Modifier.weight(1f), size = 18.sp, color = if (settings.shows(section)) c.fg else c.dim, maxLines = 1)
                    FocusSwitch(settings.shows(section))
                }
                // A section's own lines only while it is switched on: the page stays short.
                // The note can show another app's own widget instead of Focus's text card.
                if (settings.shows(section) && section == HomeSection.NOTE) {
                    val id = settings.noteWidget
                    val showing = remember(id) { if (id == 0) null else WidgetHost.labelOf(context, id) }
                    SubLine("Shows:  " + (showing ?: "Focus's own text card") + "   ·   change") { picking = section }
                }
                if (settings.shows(section) && section == HomeSection.CALENDAR) {
                    val found = calendars
                    when {
                        !CalendarRepository.hasAccess(context) -> SubLine("Calendar:  needs calendar access   ·   allow") { askCalendar.launch(Manifest.permission.READ_CALENDAR) }
                        found == null -> Unit
                        found.isEmpty() -> {
                            SubLine("Calendar:  none found")
                            // The picker carries this explanation; with nothing to pick from it has to stand here.
                            if (workBlocked) {
                                Note(
                                    "Calendars inside your Work profile are not listed: the organisation that manages it does not let " +
                                        "other apps read them, and Focus respects that.",
                                )
                            }
                        }
                        else -> SubLine("Calendar:  " + (if (settings.calendarKey == CALENDAR_ALL) "All" else shownCalendar?.shortName ?: "None found") + "   ·   change") { pickingCalendar = true }
                    }
                    SubLine("Week strip:  " + if (settings.showWeekStrip) "shown   ·   hide" else "not shown   ·   show") {
                        Graph.settings.update { it.copy(showWeekStrip = !it.showWeekStrip) }
                    }
                }
            }
        }
    }

    if (pickingCalendar) {
        CalendarPickerDialog(
            calendars = calendars.orEmpty(),
            upcomingCounts = calendarCounts,
            selectedKey = if (settings.calendarKey == CALENDAR_ALL) CALENDAR_ALL else shownCalendar?.key,
            workProfileBlocked = workBlocked,
            onDismiss = { pickingCalendar = false },
            onPick = { key -> Graph.settings.update { it.copy(calendarKey = key) } },
        )
    }

    picking?.let { section ->
        // Loading a widget's label opens that app's resources; with dozens of them, off the main thread.
        var choices by remember(section) { mutableStateOf<List<WidgetChoice>?>(null) }
        LaunchedEffect(section) { choices = withContext(Dispatchers.IO) { WidgetHost.choices(context) } }
        WidgetPickerDialog(
            title = "Note shows",
            none = "No note or to-do app on this phone offers a widget.",
            choices = choices ?: emptyList(),
            loading = choices == null,
            onDismiss = { picking = null },
            onOwn = { use(section, 0) },
        ) { choice ->
            val (id, ask) = WidgetHost.bind(context, choice)
            if (ask == null) use(section, id) else {
                awaiting = section to id
                askBind.launch(ask)
            }
        }
    }
}

/** A quiet line under a section's row: what it is set to, then the word that changes it. */
@Composable
private fun SubLine(text: String, onClick: (() -> Unit)? = null) {
    T(
        text,
        Modifier.fillMaxWidth().press(enabled = onClick != null) { onClick?.invoke() }.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        size = 13.sp, color = LocalFocusColors.current.dim, maxLines = 1,
    )
}

/**
 * The widgets that suit the slot, searchable; the first row goes back to Focus's own card. A phone
 * offers dozens of widgets that have nothing to do with a calendar or a note, so the rest wait
 * behind "All widgets…". Typing searches all of them.
 */
@Composable
private fun WidgetPickerDialog(title: String, none: String, choices: List<WidgetChoice>, loading: Boolean, onDismiss: () -> Unit, onOwn: () -> Unit, onPick: (WidgetChoice) -> Unit) {
    val c = LocalFocusColors.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var all by remember { mutableStateOf(false) }
    val q = query.text.trim()
    val shown = remember(choices, q, all) {
        when {
            q.isNotEmpty() -> choices.filter { it.appLabel.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true) }
            all -> choices
            else -> choices.filter { it.relevant }
        }
    }
    FocusDialog(onDismiss, title, "Another app's widget is drawn by that app. Focus rounds it and takes the colour out; it cannot make it text-only.", tall = true) {
        UnderlinedField(query, { query = it }, placeholder = "Search all widgets", modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), imeAction = ImeAction.Search)
        LazyColumn(Modifier.weight(1f)) {
            if (q.isEmpty()) item(key = "own") {
                MenuRow("Focus's own text card", detail = "default") {
                    onOwn()
                    onDismiss()
                }
            }
            items(shown.size, key = { shown[it].info.provider.flattenToString() + shown[it].info.profile.hashCode() }) { index ->
                val choice = shown[index]
                MenuRow(choice.appLabel + "  ·  " + choice.label, detail = if (choice.work) "work" else null) {
                    onPick(choice)
                    onDismiss()
                }
            }
            if (shown.isEmpty()) item(key = "none") {
                T(if (loading) "Looking…" else if (q.isEmpty()) none else "No widget matches “$q”.", Modifier.padding(horizontal = 24.dp, vertical = 14.dp), size = 14.sp, color = c.dim)
            }
            if (q.isEmpty() && !all && choices.any { !it.relevant }) item(key = "all") {
                T("All widgets…", Modifier.fillMaxWidth().press { all = true }.padding(horizontal = 24.dp, vertical = 14.dp), size = 14.sp, color = c.dim)
            }
        }
    }
}
