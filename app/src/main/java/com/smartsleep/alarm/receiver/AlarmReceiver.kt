package com.smartsleep.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import android.os.VibrationEffect
import com.smartsleep.alarm.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("AlarmReceiver", "Alarm Received!")
        
        try {
            // 0. WakeLock: صعقة كهربائية للمعالج للاستيقاظ فوراً
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "DreamPulse:AlarmWakeLock"
            )
            wakeLock.acquire(10000)

            // 1. Prepare Full Screen Intent
            val activityIntent = Intent(context, com.smartsleep.alarm.ui.AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, System.currentTimeMillis().toInt(), activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 1. التشغيل القسري للواجهة فوراً كأولوية قصوى
            context.startActivity(activityIntent)

            // 2. تشغيل خدمة المنبه (المسؤولة عن الإشعار والصوت)
            val serviceIntent = Intent(context, com.smartsleep.alarm.service.AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AlarmReceiver", "Error in onReceive", e)
        }
    }
}
