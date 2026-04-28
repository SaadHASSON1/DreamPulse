package com.smartsleep.alarm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.Vibrator
import android.os.VibrationEffect
import com.smartsleep.alarm.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service(), android.hardware.SensorEventListener {

    @Inject lateinit var notificationHelper: NotificationHelper
    private var vibrator: Vibrator? = null
    private var sensorManager: android.hardware.SensorManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private val SHAKE_THRESHOLD = 18.0f 

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Start Foreground
        startForeground(1, notificationHelper.getAlarmNotification())

        // 2. Play Sound
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
            e.printStackTrace()
        }

        // 3. Vibrate
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 1000, 500, 1000, 500)
        vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))

        // 5. Start Shake Sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)

        return START_STICKY
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event?.sensor?.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            if (acceleration > SHAKE_THRESHOLD) {
                // هزة قوية مكتشفة!
                // إرسال إشارة للنشاط ليظهر الملخص بدلاً من الإغلاق المفاجئ
                val intent = Intent(this, com.smartsleep.alarm.ui.AlarmActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("action", "show_summary")
                }
                startActivity(intent)
                stopSelf()
            }
        }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        vibrator?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
