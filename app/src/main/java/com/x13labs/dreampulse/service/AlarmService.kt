package com.x13labs.dreampulse.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.x13labs.dreampulse.ui.AlarmActivity
import com.x13labs.dreampulse.util.NotificationHelper
import com.x13labs.dreampulse.R

class AlarmService : Service() {

    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var serviceWakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 1. صحّي الشاشة فوراً
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        serviceWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "DreamPulse:AlarmServiceWakeLock"
        )
        serviceWakeLock?.acquire(60000)

        // 2. تأكد من وجود قناة الإشعار
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                NotificationHelper.ALARM_CHANNEL_ID, "Sleep Alarms",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        // 3. جهّز الـ PendingIntent للشاشة الكاملة
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 2001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. ابنِ الإشعار — لون أحمر زاهي للـ chip
        val alarmColor = android.graphics.Color.parseColor("#FF5252") // أحمر فاقع
        val notificationBuilder = NotificationCompat.Builder(this, NotificationHelper.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_chip)
            .setColor(alarmColor)                          // لون الـ chip على watch face
            .setColorized(true)                            // يطبّق اللون على الخلفية
            .setContentTitle("🔔 DreamPulse — WAKE UP!")
            .setContentText("اضغط لفتح شاشة المنبه")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(fullScreenPendingIntent)

        // 5. Ongoing Activity chip — أوضح وأكبر
        val alarmStatusText = Status.forPart(
            Status.TextPart("🔔 استيقظ! اضغط هنا")
        )

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            NotificationHelper.NOTIFICATION_ID + 1,
            notificationBuilder
        )
            .setAnimatedIcon(R.drawable.ic_alarm_chip)
            .setStaticIcon(R.drawable.ic_alarm_chip)
            .setTouchIntent(fullScreenPendingIntent)
            .setStatus(alarmStatusText)
            .build()

        ongoingActivity.apply(applicationContext)

        val notification = notificationBuilder.build()


        // ✅ 6. startForeground أولاً ← بعدها يصبح مسموحاً بـ startActivity
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID + 1,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID + 1, notification)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "startForeground failed", e)
        }

        // ✅ 7. الآن startActivity مسموح لأن الـ service أصبحت Foreground
        try {
            startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e("AlarmService", "startActivity failed: ${e.message}", e)
        }

        // 8. Auto-stop بعد 10 دقائق
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 10 * 60 * 1000L)

        // 9. صوت
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "MediaPlayer error", e)
        }

        // 10. اهتزاز
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Vibration error", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        if (serviceWakeLock?.isHeld == true) {
            serviceWakeLock?.release()
        }
        vibrator?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}