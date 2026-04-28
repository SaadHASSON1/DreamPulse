package com.smartsleep.alarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import com.smartsleep.alarm.data.repository.SleepRepository
import com.smartsleep.alarm.domain.model.SleepState
import com.smartsleep.alarm.receiver.AlarmReceiver
import com.smartsleep.alarm.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class SleepMonitorService : Service(), SensorEventListener {

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var healthServicesManager: com.smartsleep.alarm.data.sensors.HealthServicesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sleepDurationMillis: Long = 0
    private var sensorManager: SensorManager? = null
    
    // متغيرات تتبع النوم (خوارزمية سامسونج)
    private var lastMotionTime: Long = System.currentTimeMillis()
    private var lowHRStartTime: Long = 0
    private var currentHR: Float = 0f
    private var isSleepConfirmed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SleepMonitor", "Service Starting...")
        sleepDurationMillis = intent?.getLongExtra(EXTRA_SLEEP_DURATION, 8 * 3600 * 1000L) ?: 0
        
        val notificationText = "DreamPulse Active: Safe mode enabled."
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notificationHelper.getTrackingNotification(notificationText),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notificationHelper.getTrackingNotification(notificationText)
            )
        }

        // 1. جدولة منبه "احتياطي" فوراً لضمان الاستيقاظ مهما حدث
        scheduleAlarm()

        // 2. تفعيل الحساسات للمراقبة النشطة
        setupSensors()
        
        // 3. مراقبة حالة النوم (نظام + خوارزمية)
        observeSleepState()
        sleepRepository.startMonitoring()

        return START_STICKY
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val hr = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager?.registerListener(this, hr, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private var wakeUpTime: Long = 0
    private var isSmartWindowActive = false

    private fun observeSleepState() {
        serviceScope.launch {
            sleepRepository.sleepState.collectLatest { state ->
                Log.d("SleepMonitor", "System Sleep State: $state")
                if (state == SleepState.ASLEEP && !isSleepConfirmed) {
                    confirmSleep("System Signal")
                }
            }
        }
    }

    private fun confirmSleep(source: String) {
        if (isSleepConfirmed) return
        isSleepConfirmed = true
        
        Log.i("SleepMonitor", "Sleep Confirmed via $source")
        val startTime = System.currentTimeMillis()
        
        serviceScope.launch {
            sleepRepository.saveSleepStartTime(startTime)
        }
        
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(this@SleepMonitorService, "Sleep Detected ($source)!", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // تحديث المنبه ليكون أدق (بعد 5 ساعات من النوم الحقيقي)
        scheduleAlarm(startTime + sleepDurationMillis)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z) - 9.81f
                
                if (magnitude > 0.3f) { // حركة ملحوظة
                    lastMotionTime = System.currentTimeMillis()
                    if (isSleepConfirmed && isSmartWindowActive) {
                        // استيقاظ مبكر داخل النافذة الذكية
                        triggerAlarmNow()
                    }
                }
            }
            Sensor.TYPE_HEART_RATE -> {
                currentHR = event.values[0]
                checkHeuristic()
            }
        }
    }

    private fun checkHeuristic() {
        if (isSleepConfirmed) return
        
        val now = System.currentTimeMillis()
        val timeSinceLastMotion = now - lastMotionTime
        
        // خوارزمية سامسونج المبسطة:
        // 1. لا حركة لمدة 10 دقائق
        // 2. نبض القلب منخفض (أقل من 70 أو استقر لفترة)
        val isStationary = timeSinceLastMotion > 10 * 60 * 1000L
        val isLowHR = currentHR > 0 && currentHR < 75f
        
        if (isStationary && isLowHR) {
            if (lowHRStartTime == 0L) lowHRStartTime = now
            if (now - lowHRStartTime > 5 * 60 * 1000L) { // استقرار الحالة لـ 5 دقائق إضافية
                confirmSleep("Heuristic Engine")
            }
        } else {
            lowHRStartTime = 0
        }
    }

    private fun triggerAlarmNow() {
        val intent = Intent(this, AlarmReceiver::class.java)
        sendBroadcast(intent)
        stopSelf()
    }

    private fun scheduleAlarm(specificTime: Long = 0) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // إذا لم نحدد وقتاً، نستخدم "الآن + المدة" كمنبه طوارئ
        val targetTime = if (specificTime > 0) specificTime else (System.currentTimeMillis() + sleepDurationMillis)
        wakeUpTime = targetTime
        
        updateNotification(if (specificTime > 0) "Sleeping... Alarm set for accurate time." else "DreamPulse Active: Fallback alarm set.")

        val receiverIntent = Intent(this, AlarmReceiver::class.java)
        val receiverPendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, receiverIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(wakeUpTime, receiverPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, receiverPendingIntent)
            
            // جدولة النافذة الذكية قبل الموعد بـ 20 دقيقة
            serviceScope.launch {
                val delayTime = (wakeUpTime - System.currentTimeMillis() - 20 * 60 * 1000L).coerceAtLeast(0)
                kotlinx.coroutines.delay(delayTime)
                isSmartWindowActive = true
                updateNotification("Smart Window Active: Looking for light sleep...")
            }
        } catch (e: Exception) {
            Log.e("SleepMonitor", "Failed to set alarm", e)
        }
    }

    private fun updateNotification(text: String) {
        val notification = notificationHelper.getTrackingNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        healthServicesManager.setTracking(false)
        healthServicesManager.updateSleepState(SleepState.UNKNOWN)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SLEEP_DURATION = "extra_sleep_duration"
        private const val ALARM_REQUEST_CODE = 1001
    }
}
