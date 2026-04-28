package com.smartsleep.alarm.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.smartsleep.alarm.R
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
            "Sleep Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarms",
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
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun getAlarmNotification(): Notification {
        val activityIntent = android.content.Intent(context, com.smartsleep.alarm.ui.AlarmActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), activityIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // زر الإيقاف داخل الإشعار
        val stopIntent = android.content.Intent(context, com.smartsleep.alarm.ui.AlarmActivity::class.java).apply {
            putExtra("action", "show_summary") // توحيد الأمر مع ما تتوقعه الشاشة
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val stopPendingIntent = android.app.PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt() + 1, stopIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Wake Up!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking_channel"
        const val ALARM_CHANNEL_ID = "alarm_channel_v3" // تحديث القناة لضمان تطبيق الإعدادات الجديدة
        const val NOTIFICATION_ID = 1001
    }
}
