package com.focus.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.view.View
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.focus.launcher.Graph
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.theme.LocalFocusColors

/** One widget an installed app offers, in whichever profile that app lives. [relevant]: it suits the slot it was listed for. */
class WidgetChoice(val info: AppWidgetProviderInfo, val label: String, val appLabel: String, val work: Boolean, val relevant: Boolean)

// Fragments of a package, app or widget name that give away a calendar, or a note / to-do app.
// ponytail: a naive word list; anything it misses is one tap away under "All widgets…".
private val NOTE_WORDS = listOf("keep", "note", "memo", "todo", "to-do", "to do", "task", "checklist", "remind", "notion", "obsidian", "joplin")

/**
 * The calendar and note sections can show another app's own widget instead of Focus's text card.
 * That is how a calendar inside a managed work profile gets onto the home screen at all: its
 * events are closed to a personal-side app, but its widget is allowed wherever the organisation
 * lists the app as a cross-profile widget provider.
 *
 * A widget is drawn by its own app, so Focus cannot make it text-only; it can and does take the
 * colour out of it.
 */
object WidgetHost {
    private const val HOST_ID = 1
    private var host: AppWidgetHost? = null
    private var swept = false

    fun get(context: Context): AppWidgetHost = host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    /** Widgets only receive updates while the host listens: bracket the home screen's visible time with this. */
    fun listen(context: Context, on: Boolean) {
        val settings = Graph.settings.value
        if (on && !swept) {
            // Once per process: let go of widgets nothing shows any more (a slot that was removed,
            // a pick that was never confirmed). The system keeps them bound and updated otherwise.
            swept = true
            try {
                get(context).appWidgetIds.filter { it != settings.noteWidget }.forEach { release(context, it) }
            } catch (_: Exception) {
            }
        }
        if (settings.noteWidget == 0) return
        try {
            if (on) get(context).startListening() else get(context).stopListening()
        } catch (_: Exception) {
        }
    }

    /**
     * Every widget of every profile the system lets this app see, the ones that suit the slot
     * ([calendar], else the note) marked as such and first.
     */
    fun choices(context: Context): List<WidgetChoice> {
        val manager = AppWidgetManager.getInstance(context)
        val profiles = context.getSystemService(UserManager::class.java)?.userProfiles ?: listOf(Process.myUserHandle())
        val pm = context.packageManager
        return profiles.flatMap { profile ->
            val found = try {
                manager.getInstalledProvidersForProfile(profile)
            } catch (_: Exception) {
                emptyList()
            }
            found.map { info ->
                val pkg = info.provider.packageName
                val label = info.loadLabel(pm)
                val appLabel = Graph.apps.labelForPackage(pkg)
                val names = "$pkg\n$label\n$appLabel".lowercase()
                WidgetChoice(info, label, appLabel, work = profile != Process.myUserHandle(), relevant = NOTE_WORDS.any { it in names })
            }
        }.sortedWith(compareBy<WidgetChoice> { !it.relevant }.thenBy { it.appLabel.lowercase() }.thenBy { it.label.lowercase() })
    }

    /**
     * Reserves an id for [choice] and tries to bind it. Returns the id and, when the system wants the
     * user's say-so first, the intent that asks for it (the id only counts once that comes back OK).
     */
    fun bind(context: Context, choice: WidgetChoice): Pair<Int, Intent?> {
        val id = get(context).allocateAppWidgetId()
        val bound = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, choice.info.profile, choice.info.provider, null)
        if (bound) return id to null
        return id to Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, choice.info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, choice.info.profile)
    }

    /** Some widgets ask something first (which account, which list). Without this they stay an empty shell. */
    fun configure(activity: Activity, id: Int) {
        val info = AppWidgetManager.getInstance(activity).getAppWidgetInfo(id) ?: return
        if (info.configure == null) return
        try {
            // ponytail: the answer is not awaited; a cancelled setup leaves the widget as the app draws it unconfigured.
            get(activity).startAppWidgetConfigureActivityForResult(activity, id, 0, 0, null)
        } catch (_: Exception) {
        }
    }

    fun release(context: Context, id: Int) {
        if (id != 0) try {
            get(context).deleteAppWidgetId(id)
        } catch (_: Exception) {
        }
    }

    fun labelOf(context: Context, id: Int): String? =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.let { info ->
            Graph.apps.labelForPackage(info.provider.packageName) + "  ·  " + info.loadLabel(context.packageManager)
        }
}

private val GREY = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }

/** Another app's widget in whatever cell it is given, rounded like Focus's own cards and drained of colour. */
@Composable
fun HostedWidget(id: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember(id) { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }
    if (info == null) {
        T("This widget is gone. Pick another in Arrange home screen.", modifier, size = 14.sp, color = LocalFocusColors.current.dim)
        return
    }
    // The same panel as Focus's own cards behind it, so every card on the page has the same outer
    // edge whatever margins the other app draws inside its widget.
    BoxWithConstraints(modifier.clip(RoundedCornerShape(18.dp)).background(LocalFocusColors.current.line.copy(alpha = 0.6f))) {
        val widthDp = maxWidth.value.toInt()
        val heightDp = maxHeight.value.toInt()
        // Keyed: an AndroidView keeps the view its factory made. Without the key, a slot that is handed
        // a different widget id (cards changed places) goes on showing the previous widget.
        key(id) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // Application context: the host lives as long as the process and keeps the view it made.
                    WidgetHost.get(ctx).createView(ctx.applicationContext, id, info).apply {
                        setPadding(0, 0, 0, 0)
                        setLayerType(View.LAYER_TYPE_HARDWARE, GREY)
                    }
                },
                // The widget is told how much room it really has, again whenever that changes: many
                // switch to a smaller layout of their own instead of being cut off mid-content.
                update = { view ->
                    val size = widthDp * 10_000 + heightDp
                    if (view.tag != size) {
                        view.tag = size
                        // ponytail: one fixed size per fit; per-widget resizing if anyone asks for it.
                        @Suppress("DEPRECATION")
                        view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
                    }
                },
            )
        }
    }
}
