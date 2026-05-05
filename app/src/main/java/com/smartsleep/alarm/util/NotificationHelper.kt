package com.smartsleep.alarm.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.smartsleep.alarm.R
import com.smartsleep.alarm.ui.AlarmActivity
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
            "Sleep Tracking Status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows live sleep tracking data"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Sleep Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for sleep alarms"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(trackingChannel)
        notificationManager.createNotificationChannel(alarmChannel)
    }

    fun getTrackingNotification(content: String): Notification {
        return NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setContentTitle("DreamPulse Tracking")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .build()
    }

    fun getAlarmNotification(): Notification {
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 1001, activityIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("action", "show_summary")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val stopPendingIntent = PendingIntent.getActivity(
            context, 1002, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setContentTitle("DreamPulse Alarm")
            .setContentText("Wake Up!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(false)
            .build()
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking_channel_v3"
        const val ALARM_CHANNEL_ID = "alarm_channel_v5"
        const val NOTIFICATION_ID = 1001
    }
}
