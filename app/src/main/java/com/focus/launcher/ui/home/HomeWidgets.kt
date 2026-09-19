package com.focus.launcher.ui.home

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.compose.LifecycleStartEffect
import com.focus.launcher.service.MediaListener
import com.focus.launcher.util.Perms
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import android.media.AudioManager
import android.view.KeyEvent
import com.focus.launcher.ui.components.hasColourGlyphs
import com.focus.launcher.ui.components.monochrome
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.focus.launcher.data.CalEvent
import com.focus.launcher.data.ClockStyle
import com.focus.launcher.data.DayUsage
import com.focus.launcher.data.HomeAlign
import com.focus.launcher.data.RingMode
import com.focus.launcher.data.Settings
import com.focus.launcher.data.TimeFormat
import com.focus.launcher.ui.components.HSpace
import com.focus.launcher.ui.components.Label
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.VSpace
import com.focus.launcher.ui.components.press
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.util.formatDuration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The user's locale, read so that a language change recomposes whatever formats text with it. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** The current time, refreshed on each minute boundary and only while the screen is visible. */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val owner = LocalLifecycleOwner.current
    return produceState(LocalDateTime.now(), owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(60_000 - System.currentTimeMillis() % 60_000 + 40)
            }
        }
    }
}

fun HomeAlign.horizontal(): Alignment.Horizontal = when (this) {
    HomeAlign.LEFT -> Alignment.Start
    HomeAlign.CENTER -> Alignment.CenterHorizontally
    HomeAlign.RIGHT -> Alignment.End
}

fun HomeAlign.text(): TextAlign = when (this) {
    HomeAlign.LEFT -> TextAlign.Start
    HomeAlign.CENTER -> TextAlign.Center
    HomeAlign.RIGHT -> TextAlign.End
}

private val DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val DATE_SHORT = DateTimeFormatter.ofPattern("EEE, d MMM")

data class BatteryState(val percent: Int, val charging: Boolean)

private fun readBattery(context: Context, update: Intent?): BatteryState {
    val intent = update ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return BatteryState(
        percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
    )
}

/** Battery level and charging state, followed only while the screen it is shown on is visible. */
@Composable
fun rememberBattery(): State<BatteryState> {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    return produceState(readBattery(context, null), context, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    value = readBattery(c, intent)
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let {
                value = readBattery(context, it)
            }
            try {
                awaitCancellation()
            } finally {
                context.unregisterReceiver(receiver)
            }
        }
    }
}

/**
 * Date and time. In ring style the bright arc is either the battery (a full circle is 100%) or
 * the part of today that has already passed, starting at the top. The whole clock is one touch
 * target that lights up like everything else: a tap runs the action chosen by the user (usually
 * "open this app"), a long-press lets them choose it.
 */
@Composable
fun HomeClock(
    settings: Settings,
    now: LocalDateTime,
    ringSize: Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val locale = currentLocale()
    val use24h = when (settings.timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
    }
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
    val amPm = if (use24h) null else now.format(DateTimeFormatter.ofPattern("a", locale)).uppercase()

    val showBattery = settings.ringMode == RingMode.BATTERY
    val battery by rememberBattery()
    // The lowest line sits where the circle is already narrowing, so a small ring gets short text.
    val compact = settings.clockStyle == ClockStyle.RING && ringSize < 172.dp
    val separator = if (compact) " " else "  ·  "
    val batteryText = when {
        battery.charging -> "${battery.percent}%${separator}charging"
        battery.percent <= 15 -> "${battery.percent}%${separator}low"
        else -> "${battery.percent}%"
    }
    val tap = Modifier.combinedClickable(onLongClick = onLongPress, onClick = onTap)

    if (settings.clockStyle == ClockStyle.RING) {
        val target = if (showBattery) battery.percent / 100f else (now.hour * 60 + now.minute) / 1440f
        // Sweeps in from empty the first time, then glides to every new value. The animated value
        // is only read while drawing, so a moving arc never recomposes the clock.
        val fill = remember { Animatable(0f) }
        LaunchedEffect(target) { fill.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing)) }

        Box(modifier.size(ringSize).clip(CircleShape).then(tap), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2 + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(c.line, 0f, 360f, false, topLeft, arcSize, style = Stroke(1.5.dp.toPx()))
                val sweep = 360f * fill.value.coerceIn(0f, 1f)
                drawArc(c.fg, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // A bead at the end of the arc, so even a nearly empty ring has a clear reading.
                val radius = arcSize.width / 2
                val angle = Math.toRadians((sweep - 90f).toDouble())
                val bead = Offset(size.width / 2 + radius * cos(angle).toFloat(), size.height / 2 + radius * sin(angle).toFloat())
                drawCircle(c.bg, 6.dp.toPx(), bead)
                drawCircle(c.fg, 3.5.dp.toPx(), bead)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T(time, size = (ringSize.value * (if (compact) 0.215f else 0.235f)).sp, weight = FontWeight.Light, maxLines = 1)
                    if (amPm != null) {
                        HSpace(4.dp)
                        T(amPm, Modifier.padding(bottom = (ringSize.value * 0.04f).dp), size = 12.sp, color = c.dim, maxLines = 1)
                    }
                }
                if (settings.showDate) {
                    VSpace(if (compact) 0.dp else 2.dp)
                    T(now.format(DATE_SHORT), size = if (compact) 13.sp else 14.sp, color = c.dim, maxLines = 1)
                }
                if (showBattery) {
                    VSpace(if (compact) 2.dp else 5.dp)
                    val urgent = battery.percent <= 15 && !battery.charging
                    T(batteryText, size = if (compact) 11.sp else 12.sp, color = if (urgent) c.fg else c.faint, maxLines = 1)
                }
            }
        }
    } else {
        Column(modifier.then(tap), horizontalAlignment = settings.homeAlign.horizontal()) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(time, size = 68.sp, weight = FontWeight.Light, maxLines = 1)
                if (amPm != null) {
                    HSpace(6.dp)
                    T(amPm, Modifier.padding(bottom = 14.dp), size = 15.sp, color = c.dim, maxLines = 1)
                }
            }
            val line = listOfNotNull(
                now.format(DATE_LONG).takeIf { settings.showDate },
                batteryText.takeIf { showBattery },
            ).joinToString("   ·   ")
            if (line.isNotEmpty()) T(line, Modifier.padding(vertical = 4.dp), size = 17.sp, color = c.dim, maxLines = 1)
        }
    }
}

/**
 * One bar for the whole day: 24 cells, one per hour. The bright part of each cell is the share of
 * that hour spent on the phone, so the total amount of white is today's screen time out of 24h.
 */
@Composable
fun DayBar(perHour: LongArray, modifier: Modifier = Modifier, height: Dp = 12.dp, currentHour: Int? = null) {
    val c = LocalFocusColors.current
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().height(height + 5.dp)) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * 23) / 24f
        val barHeight = height.toPx()
        for (h in 0 until 24) {
            val x = h * (cell + gap)
            drawRect(c.line, Offset(x, 0f), Size(cell, barHeight))
            val share = (perHour[h].toFloat() / DayUsage.HOUR_MS).coerceIn(0f, 1f) * grow.value
            if (share > 0f) drawRect(c.fg, Offset(x, 0f), Size((cell * share).coerceAtLeast(1f), barHeight))
            if (h == currentHour) drawRect(c.dim, Offset(x, barHeight + 3.dp.toPx()), Size(cell, 1.5.dp.toPx()))
        }
    }
}

@Composable
fun HourScale(modifier: Modifier = Modifier) {
    val c = LocalFocusColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (mark in listOf("0", "6", "12", "18", "24")) T(mark, size = 10.sp, color = c.faint, maxLines = 1)
    }
}

private fun unlocksText(today: DayUsage?): String = (today?.unlocks ?: 0).let { if (it == 1) "1 unlock" else "$it unlocks" }

/**
 * Today's screen time, said plainly under the clock: a small title, the total in type large
 * enough to read at a glance, and what share of the day's 24 hours that is. A tap opens the review, where the hour-by-hour picture lives.
 */
@Composable
fun ScreenTimeLine(
    today: DayUsage?, hasAccess: Boolean, align: Alignment.Horizontal, onClick: () -> Unit, modifier: Modifier = Modifier,
    /** One line, for a home screen that has run out of room: "Screen Time  26m  · 1% of today". */
    compact: Boolean = false,
) {
    val c = LocalFocusColors.current
    if (compact && hasAccess) {
        val total = today?.total ?: 0L
        Row(modifier.press(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            T("Screen Time", Modifier.padding(bottom = 1.dp), size = 14.sp, color = c.dim, maxLines = 1)
            T(formatDuration(total), Modifier.padding(horizontal = 10.dp), size = 18.sp, maxLines = 1)
            T("${total * 100 / (24 * DayUsage.HOUR_MS)}%  ·  ${unlocksText(today)}", Modifier.padding(bottom = 1.dp), size = 13.sp, color = c.dim, maxLines = 1)
        }
        return
    }
    Column(modifier.press(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = align) {
        T("Screen Time", size = 15.sp, color = c.dim, maxLines = 1)
        VSpace(3.dp)
        if (hasAccess) {
            val total = today?.total ?: 0L
            T(formatDuration(total), size = 24.sp, maxLines = 1)
            // Of all 24 hours, sleep included: the same yardstick every day, at any time of day.
            // How often the phone was picked up says as much as how long it was held.
            T("${total * 100 / (24 * DayUsage.HOUR_MS)}% of today  ·  ${unlocksText(today)}", size = 13.sp, color = c.dim, maxLines = 1)
        } else {
            T("Allow usage access  →", size = 14.sp, color = c.dim, maxLines = 1)
        }
    }
}

/** The room a card has: beside another card, the full width to itself, or one line on a full screen. */
enum class TileShape { SQUARE, WIDE, STRIP }

private fun Modifier.panel(fill: Color, onClick: (() -> Unit)?) = this
    .clip(RoundedCornerShape(18.dp))
    .background(fill)
    .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)

/**
 * A section of the home screen as a card: a soft rounded panel, one shade off the background, with
 * a small title and its content underneath. Two narrow ones share a row; see `HomeScreen`.
 */
@Composable
fun Tile(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.panel(LocalFocusColors.current.line.copy(alpha = 0.6f), onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Label(title)
        VSpace(8.dp)
        content()
    }
}

/** The same card on a screen with no room left: its title and one line of content, side by side. */
@Composable
fun Strip(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().panel(LocalFocusColors.current.line.copy(alpha = 0.6f), onClick).heightIn(min = STRIP_HEIGHT.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title)
        HSpace(14.dp)
        content()
    }
}

const val STRIP_HEIGHT = 40f

/** What the active player says about itself. */
/** A data class on purpose: a player that reports the same thing again changes no state, so nothing redraws. */
private data class NowPlaying(val controller: MediaController, val title: String?, val artist: String?, val playing: Boolean)

/**
 * The player that is active right now, while the home screen is visible and only then. Needs
 * notification access ([MediaListener]); without it this stays null and the card falls back to
 * media keys. Everything here is pushed by the system: nothing polls.
 */
@Composable
private fun rememberNowPlaying(hasAccess: Boolean): NowPlaying? {
    val context = LocalContext.current
    var now by remember { mutableStateOf<NowPlaying?>(null) }
    LifecycleStartEffect(hasAccess) {
        val sessions = context.getSystemService(MediaSessionManager::class.java)
        val component = MediaListener.component(context)
        var watched: MediaController? = null
        val callback = object : MediaController.Callback() {
            // Players report their position every second or so. Take what the callback hands over
            // instead of fetching everything again (the metadata carries the album art).
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                now = now?.copy(title = metadata.title(), artist = metadata.artist())
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                now = now?.copy(playing = state?.state == PlaybackState.STATE_PLAYING)
            }
            override fun onSessionDestroyed() { now = null }
        }
        fun watch(controllers: List<MediaController>?) {
            watched?.unregisterCallback(callback)
            // The system lists the session that would receive a media key first.
            watched = controllers?.firstOrNull()?.also { it.registerCallback(callback) }
            now = watched?.read()
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        if (hasAccess && sessions != null) {
            try {
                sessions.addOnActiveSessionsChangedListener(listener, component)
                watch(sessions.getActiveSessions(component))
            } catch (_: SecurityException) {
                now = null
            }
        } else {
            now = null
        }
        onStopOrDispose {
            watched?.unregisterCallback(callback)
            sessions?.removeOnActiveSessionsChangedListener(listener)
        }
    }
    return now
}

private fun MediaMetadata?.title(): String? = this?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }

private fun MediaMetadata?.artist(): String? =
    (this?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: this?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.takeIf { it.isNotBlank() }

private fun MediaController.read(): NowPlaying {
    val data = metadata
    return NowPlaying(this, data.title(), data.artist(), playing = playbackState?.state == PlaybackState.STATE_PLAYING)
}

/**
 * The song, who it is by, and previous · play or pause · next. With notification access the card
 * talks to the active player directly and knows what is playing. Without it the three controls
 * still work, as media keys that Android hands to the last-used player, and one line offers the
 * access that would bring the song's name. [roomy] = a square tall enough for two lines of title.
 */
@Composable
fun MusicTile(resumeCount: Int, hasAccess: Boolean, shape: TileShape, roomy: Boolean, modifier: Modifier = Modifier) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val audio = context.getSystemService(AudioManager::class.java)
    val scope = rememberCoroutineScope()
    val now = rememberNowPlaying(hasAccess)

    // Only consulted without a controller: the audio system's word on whether something plays.
    var keyPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(resumeCount) { keyPlaying = audio?.isMusicActive == true }
    val playing = now?.playing ?: keyPlaying

    fun key(code: Int) {
        audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        // The player needs a moment to act on the key before it can be asked whether it plays.
        scope.launch {
            delay(400)
            keyPlaying = audio?.isMusicActive == true
        }
    }
    val controls = now?.controller?.transportControls
    val wide = shape == TileShape.WIDE

    @Composable
    fun Song(modifier: Modifier, size: Float, maxLines: Int) {
        val title = now?.title ?: if (now != null || playing) "Playing" else "Nothing playing"
        T(
            title,
            modifier.then(if (hasColourGlyphs(title)) Modifier.monochrome() else Modifier),
            size = size.sp, color = if (now?.title != null) c.fg else c.dim, maxLines = maxLines, lineHeight = (size + 4).sp,
        )
    }

    @Composable
    fun AccessHint(text: String) = T(
        text,
        Modifier.press { Perms.start(context, Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.padding(vertical = 4.dp),
        size = 13.sp, color = c.faint, maxLines = 1,
    )

    // The signs every player uses, drawn in the text colour. The padding around each makes the
    // touch target; half a screen is about 120dp inside the card, so they sit closer there.
    @Composable
    fun Controls(modifier: Modifier, arrangement: Arrangement.Horizontal) {
        Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
            val button = Modifier.padding(horizontal = if (wide) 14.dp else 10.dp, vertical = if (shape == TileShape.STRIP) 10.dp else 8.dp)
            val side = if (wide) 16.dp else 14.dp
            MediaGlyph(Glyph.PREVIOUS, side, c.dim, Modifier.press { controls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }.then(button))
            MediaGlyph(
                if (playing) Glyph.PAUSE else Glyph.PLAY, if (wide) 20.dp else 17.dp, c.fg,
                Modifier.press {
                    if (controls == null) key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) else if (playing) controls.pause() else controls.play()
                }.then(button),
            )
            MediaGlyph(Glyph.NEXT, side, c.dim, Modifier.press { controls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT) }.then(button))
        }
    }

    val open = now?.let { playing -> { openPlayer(context, playing.controller) } }
    when (shape) {
        TileShape.STRIP -> Strip("Music", modifier, open) {
            Song(Modifier.weight(1f), size = 14f, maxLines = 1)
            Controls(Modifier, Arrangement.spacedBy(8.dp))
        }
        TileShape.SQUARE -> Tile("Music", modifier, open) {
            Song(Modifier, size = 15f, maxLines = if (roomy) 2 else 1)
            if (!hasAccess) AccessHint("Song's name  →")
            else if (roomy) now?.artist?.let { T(it, size = 13.sp, color = c.dim, maxLines = 1) }
            Spacer(Modifier.weight(1f))
            Controls(Modifier.fillMaxWidth(), Arrangement.SpaceBetween)
        }
        TileShape.WIDE -> Tile("Music", modifier, open) {
            Song(Modifier, size = 17f, maxLines = 1)
            now?.artist?.let { T(it, size = 13.sp, color = c.dim, maxLines = 1) }
            VSpace(6.dp)
            Controls(Modifier.fillMaxWidth(), Arrangement.SpaceBetween)
            if (!hasAccess) AccessHint("Show the song's name  →")
        }
    }
}

private enum class Glyph(val says: String) { PREVIOUS("Previous"), PLAY("Play"), PAUSE("Pause"), NEXT("Next") }

/**
 * Play, pause, previous, next as the plain shapes they are everywhere: a triangle, two bars, a
 * triangle against a bar. Drawn, like the work badge, so they stay the text's colour; the Unicode
 * characters for them turn into colour emoji on many phones.
 */
@Composable
private fun MediaGlyph(glyph: Glyph, side: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(side).semantics { contentDescription = glyph.says }) {
        val w = size.width
        val h = size.height
        fun triangle(from: Float, to: Float) = drawPath(
            Path().apply {
                moveTo(from, 0f)
                lineTo(to, h / 2)
                lineTo(from, h)
                close()
            },
            color,
        )
        val bar = w * 0.16f
        when (glyph) {
            Glyph.PLAY -> triangle(w * 0.12f, w)
            Glyph.PAUSE -> {
                drawRect(color, Offset(w * 0.14f, 0f), Size(w * 0.26f, h))
                drawRect(color, Offset(w * 0.60f, 0f), Size(w * 0.26f, h))
            }
            Glyph.NEXT -> {
                triangle(0f, w - bar - w * 0.06f)
                drawRect(color, Offset(w - bar, 0f), Size(bar, h))
            }
            Glyph.PREVIOUS -> {
                drawRect(color, Offset(0f, 0f), Size(bar, h))
                triangle(w, bar + w * 0.06f)
            }
        }
    }
}

/** The player's own "now playing" screen if it offers one, else just the player. */
private fun openPlayer(context: Context, controller: MediaController) {
    try {
        controller.sessionActivity?.send() ?: context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let { Perms.start(context, it) }
    } catch (_: Exception) {
    }
}

/** A few lines of your own, kept by Focus. A tap edits them. */
@Composable
fun NoteTile(note: String, shape: TileShape, maxLines: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFocusColors.current
    val shownNote = if (shape == TileShape.STRIP) note.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim() else note

    @Composable
    fun Words(modifier: Modifier) {
        if (note.isBlank()) T("Tap to write", modifier, size = 14.sp, color = c.dim, maxLines = 1)
        else T(shownNote, modifier.then(if (hasColourGlyphs(shownNote)) Modifier.monochrome() else Modifier), size = 14.sp, maxLines = maxLines, lineHeight = 20.sp)
    }
    when (shape) {
        TileShape.STRIP -> Strip("Note", modifier, onClick) { Words(Modifier.weight(1f)) }
        // Like the alarm beside it: title on top, the words resting on the bottom edge.
        TileShape.SQUARE -> Tile("Note", modifier, onClick) {
            Spacer(Modifier.weight(1f))
            Words(Modifier)
        }
        TileShape.WIDE -> Tile("Note", modifier, onClick) { Words(Modifier) }
    }
}

/**
 * A short agenda from one calendar: the next few events, as text. The Mon-Sun strip with today
 * marked is optional and off by default, since the clock above already carries the date.
 */
@Composable
fun CalendarWidget(
    today: LocalDate,
    mondayStart: Boolean,
    events: List<CalEvent>,
    hasAccess: Boolean,
    use24h: Boolean,
    onClick: () -> Unit,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
    maxEvents: Int = 3,
    calendarName: String? = null,
    showWeekStrip: Boolean = false,
) {
    val c = LocalFocusColors.current
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Label("Calendar", Modifier.weight(1f))
            if (calendarName != null && hasAccess) T(calendarName, size = 12.sp, color = c.faint, maxLines = 1)
        }
        VSpace(10.dp)
        if (showWeekStrip) {
            WeekStrip(today, mondayStart)
            VSpace(10.dp)
        }
        when {
            !hasAccess -> T("Show upcoming events  →", Modifier.clickable(onClick = onRequestAccess).padding(vertical = 4.dp), size = 14.sp, color = c.dim)
            events.isEmpty() -> T("Nothing in the next 7 days", size = 14.sp, color = c.dim)
            else -> for (event in events.take(maxEvents)) {
                Row(Modifier.padding(vertical = 3.dp)) {
                    T(eventWhen(event, today, use24h), Modifier.width(118.dp), size = 14.sp, color = c.dim, maxLines = 1)
                    T(event.title, Modifier.weight(1f), size = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, mondayStart: Boolean) {
    val c = LocalFocusColors.current
    val locale = currentLocale()
    val weekStart = com.focus.launcher.data.WeekSummary.weekStartOf(today, mondayStart)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0L..6L) {
            val day = weekStart.plusDays(i)
            val isToday = day == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale), size = 11.sp, color = if (isToday) c.fg else c.faint, maxLines = 1)
                VSpace(5.dp)
                Box(
                    Modifier.size(30.dp).then(if (isToday) Modifier.background(c.fg) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        day.dayOfMonth.toString(), size = 14.sp,
                        color = when {
                            isToday -> c.bg
                            day.isBefore(today) -> c.faint
                            else -> c.fg
                        },
                        weight = if (isToday) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun eventWhen(event: CalEvent, today: LocalDate, use24h: Boolean): String {
    val date = event.date()
    val day = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d"))
    }
    if (event.allDay) return "$day · all day"
    val time = Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a"))
    return "$day $time"
}
