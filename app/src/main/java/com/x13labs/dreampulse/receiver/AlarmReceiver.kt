package com.x13labs.dreampulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log
import com.x13labs.dreampulse.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM = "com.x13labs.dreampulse.ACTION_ALARM"
        private const val REQUEST_CODE = 1001

        /** The one alarm PendingIntent: the service and the boot receiver must build the same one. */
        fun pendingIntent(context: Context): android.app.PendingIntent = android.app.PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).apply { action = ACTION_ALARM },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        /** Shown as the next alarm by the system, and allowed to ring in Doze. */
        fun schedule(context: Context, time: Long) {
            val pi = pendingIntent(context)
            (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
                .setAlarmClock(android.app.AlarmManager.AlarmClockInfo(time, pi), pi)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "🔥 ALARM BROADCAST RECEIVED!")

        try {
            // 1. اهتزاز فوري
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }

            // 2. صحّي الشاشة
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "DreamPulse:AlarmReceiverWakeLock"
            )
            wakeLock.setReferenceCounted(false)
            wakeLock.acquire(15000)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (wakeLock.isHeld) wakeLock.release()
            }, 14000)


            // AlarmService يتولى فتح شاشة المنبه بعد startForeground
            // 4. شغّل AlarmService للصوت والاهتزاز
            val serviceIntent = Intent(context, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (e: Exception) {
            Log.e("AlarmReceiver", "CRITICAL ERROR in onReceive", e)
        }
    }
}