package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject

enum class FontChoice(val label: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono") }

enum class ClockStyle(val label: String) { RING("Ring"), PLAIN("Plain") }

/** What the arc of the clock ring stands for. */
enum class RingMode(val label: String) { BATTERY("Battery level"), DAY("Day passed") }

/** How an app appears when it is opened from the launcher. */
enum class LaunchAnimation(val label: String) { FAST("Fast"), SYSTEM("System default") }

enum class HomeAlign(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }

/**
 * The movable parts of the home screen, between the clock on top and the corner shortcuts at the
 * bottom. All of them are plain text; none is a hosted widget.
 */
enum class HomeSection(val label: String) {
    SCREEN_TIME("Screen time"), CALENDAR("Calendar"), MUSIC("Music controls"), NOTE("Note"), APPS("Fast apps")
}

/** Order of the app list in the drawer. */
enum class DrawerSort(val label: String) { ALPHA("A–Z"), MOST_USED("Most used"), RECENT("Recent") }

enum class TimeFormat(val label: String) { SYSTEM("Follow system"), H24("24-hour"), H12("12-hour") }

const val MAX_FAVORITES = 5

/** Corner shortcuts that resolve to whatever the phone's default dialer / camera is. */
const val SHORTCUT_PHONE = "auto:phone"
const val SHORTCUT_CAMERA = "auto:camera"

/** What a tap on the clock does. Anything that is not one of these is an [AppEntry.key] to open. */
const val TAP_ALARMS = "tap:alarms"
const val TAP_CALENDAR = "tap:calendar"
const val TAP_SCREEN_TIME = "tap:screentime"
const val TAP_BATTERY = "tap:battery"
const val TAP_NOTHING = "tap:none"

/**
 * Every user-facing preference of the launcher. Immutable; changed through [SettingsStore.update].
 *
 * App references ([favorites], [hidden], [renames], shortcuts) use [AppEntry.key].
 * Timers ([appLimits]) are keyed by package name because usage is tracked per package.
 */
data class Settings(
    // Appearance
    val dark: Boolean = true,
    val font: FontChoice = FontChoice.SANS,
    val textScale: Float = 1f,
    val hideStatusBar: Boolean = false,
    val launchAnimation: LaunchAnimation = LaunchAnimation.FAST,

    // Home
    val clockStyle: ClockStyle = ClockStyle.RING,
    val ringMode: RingMode = RingMode.BATTERY,
    val clockTap: String = TAP_ALARMS,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showDate: Boolean = true,
    val showCalendar: Boolean = false,
    /** [CalendarInfo.key] of the single calendar shown on the home screen, or [CALENDAR_AUTO] / [CALENDAR_ALL]. */
    val calendarKey: String = CALENDAR_AUTO,
    /** The Mon-Sun strip with today marked. Off: the ring already carries the date. */
    val showWeekStrip: Boolean = false,
    /** Top to bottom. Holds every [HomeSection], shown or not; see [shows]. */
    val homeOrder: List<HomeSection> = HomeSection.entries,
    val showMusic: Boolean = false,
    val showNote: Boolean = false,
    /** The text of the note section. */
    val note: String = "",
    /** Id of another app's widget shown in place of Focus's own note card; 0 = Focus's own. */
    val noteWidget: Int = 0,
    val homeAlign: HomeAlign = HomeAlign.CENTER,
    val favorites: List<String> = emptyList(),
    val showShortcuts: Boolean = true,
    val leftShortcut: String = SHORTCUT_PHONE,
    val rightShortcut: String = SHORTCUT_CAMERA,

    // Drawer
    val autoKeyboard: Boolean = false,
    val autoLaunch: Boolean = false,
    val showRecentInstalls: Boolean = true,
    val showUsageInDrawer: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.ALPHA,
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),

    // App timers
    val timersEnabled: Boolean = true,
    /** Daily minutes applied to every social app without its own limit. 0 = off. */
    val socialDefaultMin: Int = 30,
    /** Daily minutes applied to every game without its own limit. 0 = off. */
    val gameDefaultMin: Int = 30,
    /** Daily minutes applied to every video / streaming app without its own limit. 0 = off. */
    val videoDefaultMin: Int = 0,
    /** package -> daily minutes. 0 means "explicitly unlimited", overriding the category default. */
    val appLimits: Map<String, Int> = emptyMap(),
    val allowContinue: Boolean = true,
    val continueOptions: List<Int> = listOf(1, 5, 15),
    val allowBypass: Boolean = true,
    /** After "ignore the limit for today", still ask before every single open of that app. */
    val askAfterBypass: Boolean = true,
    /** Seconds the "continue" choices stay locked after the wall appears. */
    val frictionSeconds: Int = 5,
    /** Heads-up this many minutes before a limit runs out. 0 = off. */
    val warnMinutes: Int = 1,

    // Weekly review
    val weeklyEnabled: Boolean = true,
    val weekStartsMonday: Boolean = true,
    val reviewHour: Int = 20,

    // Gestures
    val swipeDownNotifications: Boolean = true,
    val swipeUpSearch: Boolean = true,
    /** Swipe towards the page left of home (finger moves right): the phone's web search. */
    val swipeRightSearch: Boolean = true,
    val doubleTapLock: Boolean = true,
) {
    fun shows(section: HomeSection): Boolean = when (section) {
        // Always there: what Focus is for. They can be moved, not switched off.
        HomeSection.SCREEN_TIME, HomeSection.APPS -> true
        HomeSection.CALENDAR -> showCalendar
        HomeSection.MUSIC -> showMusic
        HomeSection.NOTE -> showNote
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("dark", dark)
        put("font", font.name)
        put("textScale", textScale.toDouble())
        put("hideStatusBar", hideStatusBar)
        put("launchAnimation", launchAnimation.name)

        put("clockStyle", clockStyle.name)
        put("ringMode", ringMode.name)
        put("clockTap", clockTap)
        put("timeFormat", timeFormat.name)
        put("showDate", showDate)
        put("showCalendar", showCalendar)
        put("calendarKey", calendarKey)
        put("showWeekStrip", showWeekStrip)
        put("homeOrder", JSONArray(homeOrder.map { it.name }))
        put("showMusic", showMusic)
        put("showNote", showNote)
        put("note", note)
        put("noteWidget", noteWidget)
        put("homeAlign", homeAlign.name)
        put("favorites", JSONArray(favorites))
        put("showShortcuts", showShortcuts)
        put("leftShortcut", leftShortcut)
        put("rightShortcut", rightShortcut)

        put("autoKeyboard", autoKeyboard)
        put("autoLaunch", autoLaunch)
        put("showRecentInstalls", showRecentInstalls)
        put("showUsageInDrawer", showUsageInDrawer)
        put("drawerSort", drawerSort.name)
        put("hidden", JSONArray(hidden.toList()))
        put("renames", JSONObject(renames))

        put("timersEnabled", timersEnabled)
        put("socialDefaultMin", socialDefaultMin)
        put("gameDefaultMin", gameDefaultMin)
        put("videoDefaultMin", videoDefaultMin)
        put("appLimits", JSONObject(appLimits))
        put("allowContinue", allowContinue)
        put("continueOptions", JSONArray(continueOptions))
        put("allowBypass", allowBypass)
        put("askAfterBypass", askAfterBypass)
        put("frictionSeconds", frictionSeconds)
        put("warnMinutes", warnMinutes)

        put("weeklyEnabled", weeklyEnabled)
        put("weekStartsMonday", weekStartsMonday)
        put("reviewHour", reviewHour)

        put("swipeDownNotifications", swipeDownNotifications)
        put("swipeUpSearch", swipeUpSearch)
        put("swipeRightSearch", swipeRightSearch)
        put("doubleTapLock", doubleTapLock)
    }

    companion object {
        /** Tolerant of missing keys, so older saved settings survive app updates. */
        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            return Settings(
                dark = o.optBoolean("dark", d.dark),
                font = enumOr(o.optString("font"), d.font),
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat().coerceIn(0.8f, 1.4f),
                hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
                launchAnimation = enumOr(o.optString("launchAnimation"), d.launchAnimation),

                clockStyle = enumOr(o.optString("clockStyle"), d.clockStyle),
                ringMode = enumOr(o.optString("ringMode"), d.ringMode),
                clockTap = o.optString("clockTap", d.clockTap).ifEmpty { d.clockTap },
                timeFormat = enumOr(o.optString("timeFormat"), d.timeFormat),
                showDate = o.optBoolean("showDate", d.showDate),
                showCalendar = o.optBoolean("showCalendar", d.showCalendar),
                calendarKey = o.optString("calendarKey").ifEmpty {
                    // Written for a few hours by an earlier build as a bare personal-calendar id.
                    when (val legacy = o.optLong("calendarId", -1L)) {
                        -1L -> CALENDAR_AUTO
                        -2L -> CALENDAR_ALL
                        else -> "p:$legacy"
                    }
                },
                showWeekStrip = o.optBoolean("showWeekStrip", d.showWeekStrip),
                // Saved order first; a section this install has never heard of goes to its default place at the end.
                homeOrder = o.optJSONArray("homeOrder").strings()
                    .mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name } }
                    .let { saved -> (saved + HomeSection.entries).distinct() },
                showMusic = o.optBoolean("showMusic", d.showMusic),
                showNote = o.optBoolean("showNote", d.showNote),
                note = o.optString("note", d.note),
                noteWidget = o.optInt("noteWidget", 0),
                homeAlign = enumOr(o.optString("homeAlign"), d.homeAlign),
                favorites = o.optJSONArray("favorites").strings().take(MAX_FAVORITES),
                showShortcuts = o.optBoolean("showShortcuts", d.showShortcuts),
                leftShortcut = o.optString("leftShortcut", d.leftShortcut),
                rightShortcut = o.optString("rightShortcut", d.rightShortcut),

                autoKeyboard = o.optBoolean("autoKeyboard", d.autoKeyboard),
                autoLaunch = o.optBoolean("autoLaunch", d.autoLaunch),
                showRecentInstalls = o.optBoolean("showRecentInstalls", d.showRecentInstalls),
                showUsageInDrawer = o.optBoolean("showUsageInDrawer", d.showUsageInDrawer),
                drawerSort = enumOr(o.optString("drawerSort"), d.drawerSort),
                hidden = o.optJSONArray("hidden").strings().toSet(),
                renames = o.optJSONObject("renames").stringMap(),

                timersEnabled = o.optBoolean("timersEnabled", d.timersEnabled),
                socialDefaultMin = o.optInt("socialDefaultMin", d.socialDefaultMin),
                gameDefaultMin = o.optInt("gameDefaultMin", d.gameDefaultMin),
                videoDefaultMin = o.optInt("videoDefaultMin", d.videoDefaultMin),
                appLimits = o.optJSONObject("appLimits").intMap(),
                allowContinue = o.optBoolean("allowContinue", d.allowContinue),
                continueOptions = o.optJSONArray("continueOptions").ints().ifEmpty { d.continueOptions },
                allowBypass = o.optBoolean("allowBypass", d.allowBypass),
                askAfterBypass = o.optBoolean("askAfterBypass", d.askAfterBypass),
                frictionSeconds = o.optInt("frictionSeconds", d.frictionSeconds),
                warnMinutes = o.optInt("warnMinutes", d.warnMinutes),

                weeklyEnabled = o.optBoolean("weeklyEnabled", d.weeklyEnabled),
                weekStartsMonday = o.optBoolean("weekStartsMonday", d.weekStartsMonday),
                reviewHour = o.optInt("reviewHour", d.reviewHour).coerceIn(0, 23),

                swipeDownNotifications = o.optBoolean("swipeDownNotifications", d.swipeDownNotifications),
                swipeUpSearch = o.optBoolean("swipeUpSearch", d.swipeUpSearch),
                swipeRightSearch = o.optBoolean("swipeRightSearch", d.swipeRightSearch),
                doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

        private fun JSONArray?.ints(): List<Int> =
            if (this == null) emptyList() else (0 until length()).map { optInt(it) }.filter { it > 0 }

        private fun JSONObject?.stringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (k in keys()) optString(k).takeIf { it.isNotEmpty() }?.let { out[k] = it }
            return out
        }

        private fun JSONObject?.intMap(): Map<String, Int> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, Int>()
            for (k in keys()) out[k] = optInt(k, 0)
            return out
        }
    }
}
