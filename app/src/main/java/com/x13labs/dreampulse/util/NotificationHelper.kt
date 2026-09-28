package com.x13labs.dreampulse.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.x13labs.dreampulse.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val trackingChannel = NotificationChannel(
            TRACKING_CHANNEL_ID,
            context.getString(R.string.channel_tracking),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_tracking_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            context.getString(R.string.channel_alarms),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarms_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(trackingChannel)
        notificationManager.createNotificationChannel(alarmChannel)
    }

    /**
     * Ongoing tracking notification. Wrapped in an Ongoing Activity so a moon chip shows on
     * the watch face and in recents while a session runs; tapping it reopens the app.
     */
    fun buildTracking(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 3001,
            Intent(context, com.x13labs.dreampulse.ui.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        androidx.wear.ongoing.OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_moon)
            .setTouchIntent(open)
            .setStatus(androidx.wear.ongoing.Status.forPart(androidx.wear.ongoing.Status.TextPart(title)))
            .build()
            .apply(context)
        return builder.build()
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking_channel_v3"
        const val ALARM_CHANNEL_ID = "alarm_channel_v5"
        const val NOTIFICATION_ID = 1001
    }
}
