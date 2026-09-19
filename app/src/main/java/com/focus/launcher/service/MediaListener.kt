package com.focus.launcher.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Android only tells an app which player is active, and what it is playing, if that app holds
 * notification access. This service is that key and nothing more: no notification is ever looked
 * at, stored or acted on.
 *
 * It is the *grant* that the media-session API checks, not a running service. A bound listener,
 * even an empty one, is handed every notification on the phone around the clock, so the moment
 * the system connects it, it asks to be let go again.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() = requestUnbind()

    companion object {
        fun component(context: Context) = ComponentName(context, MediaListener::class.java)

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
