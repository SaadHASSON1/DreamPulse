package com.smartsleep.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import android.os.VibrationEffect
import com.smartsleep.alarm.util.NotificationHelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("AlarmReceiver", "🔥 MASTER ALARM TRIGGERED!")
        
        try {
            // 1. صعقة كهربائية قوية لإضاءة الشاشة فوراً
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "DreamPulse:AlarmWakeLock"
            )
            wakeLock.acquire(15000)

            // 2. تجهيز شاشة المنبه
            val activityIntent = Intent(context, com.smartsleep.alarm.ui.AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 1001, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 3. بناء إشعار "كامل الشاشة" (هذا هو المفتاح في سامسونج 7)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "smart_sleep_alarm_channel"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Smart Sleep Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("WAKE UP!")
                .setContentText("It's time to rise and shine.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true) // هذا السطر هو السحر
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            // 4. إرسال الإشعار القوي (لتفعيل fullScreenIntent وإيقاظ الساعة)
            notificationManager.notify(2001, notification)

            // 5. تشغيل خدمة المنبه (المسؤولة عن الصوت والاهتزاز المستمر)
            val serviceIntent = Intent(context, com.smartsleep.alarm.service.AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 5. محاولة إضافية للتشغيل المباشر للشاشة
            context.startActivity(activityIntent)
            
        } catch (e: Exception) {
            android.util.Log.e("AlarmReceiver", "CRITICAL: Failed to trigger alarm UI/Service", e)
        }
    }
}
