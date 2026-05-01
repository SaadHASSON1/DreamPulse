/* 
 * Copyright (C) 2026 Saad - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null
    
    // متغيرات تتبع النوم المتقدمة (النسخة الآمنة)
    private var serviceStartTime: Long = 0
    private val hrWindow = mutableListOf<Float>()
    private val motionWindow = mutableListOf<Float>()
    private var lastMotionTime: Long = System.currentTimeMillis()
    private var isSleepConfirmed = false
    private var restingHeartRate = 0f
    private var motionSilenceCount = 0

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamPulse:SleepMonitor")
        wakeLock?.acquire(9 * 60 * 60 * 1000L) // 9-hour maximum timeout
        Log.d("SleepMonitor", "WakeLock acquired")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == ACTION_STOP_MONITORING) {
            sensorManager?.unregisterListener(this)
            healthServicesManager.stopPassiveSleepMonitoring()
            cancelAlarm()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // قراءة المدة وتصفير الحالات لضمان بداية نظيفة
        sleepDurationMillis = intent?.getLongExtra(EXTRA_SLEEP_DURATION, 480 * 60 * 1000L) ?: 480 * 60 * 1000L
        serviceStartTime = System.currentTimeMillis()
        isSleepConfirmed = false
        healthServicesManager.resetStates()

        // إطلاق الإشعار الأساسي مع تحديد نوع الخدمة للوصول للحساسات
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notificationHelper.getTrackingNotification("DreamPulse: Monitoring Started"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notificationHelper.getTrackingNotification("DreamPulse: Monitoring Started")
            )
        }

        if (action == ACTION_SIMULATE_SLEEP) {
            confirmSleep("Manual Simulation", isSimulation = true)
            return START_STICKY
        }

        try {
            // اهتزاز خفيف لتأكيد استلام الأمر
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.VIBRator
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))

            setupSensors()
            observeSleepState()
            observeHeartRate()
            
            // منح النظام نصف ثانية للاستعداد قبل طلب الحساسات
            serviceScope.launch {
                delay(500)
                healthServicesManager.startHeartRateMeasurement()
                delay(1000)
                healthServicesManager.startHeartRateMeasurement() // محاولة ثانية للتأكيد
            }
            
            startHeartRateDutyCycle() 
            healthServicesManager.startPassiveSleepMonitoring()
            
            android.widget.Toast.makeText(this, "DreamPulse: Tracking Active! 🚀", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SleepMonitor", "Start failed", e)
        }

        return START_STICKY
    }

    private fun startHeartRateDutyCycle() {
        serviceScope.launch {
            while (true) {
                val timeSinceStart = System.currentTimeMillis() - serviceStartTime
                
                // 1. مرحلة الاستكشاف والبداية (أول 15 دقيقة) أو نافذة الاستيقاظ الذكي
                if (timeSinceStart < 15 * 60 * 1000L || isSmartWindowActive) {
                    healthServicesManager.startHeartRateMeasurement(forceRestart = false)
                    delay(30000) 
                } 
                // 2. مرحلة النوم العميق (بعد التأكيد): توفير فائق (يغلق كل شيء ليوفر البطارية)
                else if (isSleepConfirmed) {
                    healthServicesManager.startHeartRateMeasurement(forceRestart = true)
                    delay(30000) // قياس لـ 30 ثانية
                    healthServicesManager.stopHeartRateMeasurement() // إغلاق الحساس والتمرين
                    delay(7 * 60 * 1000L) // استراحة 7 دقائق
                }
                // 3. مرحلة ما قبل النوم: قياس دوري متكرر
                else {
                    healthServicesManager.startHeartRateMeasurement(forceRestart = true)
                    delay(40000)
                    healthServicesManager.stopHeartRateMeasurement()
                    delay(2 * 60 * 1000L)
                }
            }
        }
    }

    private fun observeHeartRate() {
        serviceScope.launch {
            healthServicesManager.heartRate.collectLatest { bpm ->
                if (bpm > 30) {
                    hrWindow.add(bpm)
                    if (hrWindow.size > 20) hrWindow.removeAt(0)
                    
                    updateNotification("DreamPulse: Tracking active... [Pulse: ${bpm.toInt()} BPM]")
                    
                    // الربط الحيوي: استخدام بيانات خدمات الصحة الموثوقة لتأكيد النوم
                    checkAdvancedHeuristic()
                }
            }
        }
    }

    private fun observeSleepState() {
        serviceScope.launch {
            healthServicesManager.sleepState.collectLatest { state ->
                Log.d("SleepMonitor", "System Sleep State: $state")
                if (state == SleepState.ASLEEP && !isSleepConfirmed) {
                    val isSim = healthServicesManager.isSimulation.value
                    serviceScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@SleepMonitorService, if (isSim) "SIMULATION ACTIVE" else "System Signal: Sleep Detected", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    confirmSleep(if (isSim) "Simulation" else "System Signal", isSimulation = isSim)
                }
            }
        }
    }

    private fun setupSensors() {
        Log.d("SleepMonitor", "Registering Direct Hardware Sensors...")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val hr = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        
        // تسجيل الحساسات مباشرة في العتاد
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST, 0)
        hr?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0)
            Log.i("SleepMonitor", "Direct HR Sensor Registered")
        }
        
        Log.d("SleepMonitor", "Sensors Registered.")
    }

    private fun confirmSleep(source: String, isSimulation: Boolean = false) {
        if (isSleepConfirmed) return
        
        // صمام أمان: تقليل وقت الانتظار لـ 2 دقيقة لتسريع الاكتشاف في الاختبار
        if (!isSimulation && System.currentTimeMillis() - serviceStartTime < 2 * 60 * 1000L) {
            Log.d("SleepMonitor", "Sleep detected too early ($source), ignoring for safety.")
            return
        }

        isSleepConfirmed = true
        healthServicesManager.updateSleepState(SleepState.ASLEEP)
        val now = System.currentTimeMillis()
        Log.i("SleepMonitor", "🎉 Sleep Confirmed via $source. Duration: $sleepDurationMillis ms")

        serviceScope.launch {
            sleepRepository.saveSleepStartTime(now)
        }
        
        // 1. تقليل استهلاك الطاقة: إيقاف الحساسات بعد اكتشاف النوم
        try {
            sensorManager?.unregisterListener(this)
            healthServicesManager.stopPassiveSleepMonitoring()
        } catch (e: Exception) {
            Log.e("SleepMonitor", "Error stopping sensors", e)
        }
        
        // 2. تحديث المنبه للوقت الفعلي بدقة (بناءً على اختيار المستخدم)
        val targetWakeTime = now + sleepDurationMillis
        
        serviceScope.launch {
            val timeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(targetWakeTime))
            val confirmTimeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(now))
            sleepRepository.saveSleepSummary("$confirmTimeStr, $timeStr")
            updateNotification("SLEEP DETECTED! Alarm set for $timeStr")
            
            if (isSimulation || sleepDurationMillis < 30 * 60 * 1000L) {
                scheduleAlarmImmediate(targetWakeTime)
            } else {
                scheduleAlarm(targetWakeTime)
            }

            // Backup alarm: fires 30 minutes after the real alarm as a safety net
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val backupIntent = Intent(this@SleepMonitorService, AlarmReceiver::class.java).apply {
                action = "com.smartsleep.alarm.ACTION_ALARM"
            }
            val backupPendingIntent = PendingIntent.getBroadcast(
                this@SleepMonitorService, 1002, backupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val backupTime = targetWakeTime + (30 * 60 * 1000L)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, backupTime, backupPendingIntent)
            Log.d("SleepMonitor", "Backup alarm set for 30 min after real alarm.")
        }
        
        serviceScope.launch(Dispatchers.Main) {
            val minutes = sleepDurationMillis / (1000 * 60)
            android.widget.Toast.makeText(this@SleepMonitorService, "Sleep Confirmed! Alarm in $minutes mins", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z) - 9.81f
                val absMag = if (magnitude < 0) -magnitude else magnitude
                
                motionWindow.add(absMag)
                if (motionWindow.size > 50) motionWindow.removeAt(0)

                // تحديث الإشعار بالحركة اللحظية ليعرف المستخدم أن الحساس يعمل
                if (!isSleepConfirmed && System.currentTimeMillis() % 5000 < 1000) {
                    updateNotification("DreamPulse: Monitoring... [Motion: ${String.format("%.2f", absMag)}]")
                }

                if (absMag > 0.15f) {
                    lastMotionTime = System.currentTimeMillis()
                    if (isSleepConfirmed && isSmartWindowActive) triggerAlarmNow()
                }
            }
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0]
                if (bpm > 30) {
                    healthServicesManager.updateHeartRate(bpm)
                    hrWindow.add(bpm)
                    if (hrWindow.size > 20) hrWindow.removeAt(0)
                    
                    // تحديث الإشعار بالنبض اللحظي (دليل الحياة للحساس)
                    updateNotification("DreamPulse: Tracking active... [Pulse: ${bpm.toInt()} BPM]")
                    
                    checkAdvancedHeuristic()
                }
            }
        }
    }

    private fun checkAdvancedHeuristic() {
        if (isSleepConfirmed || hrWindow.size < 10) return
        
        val now = System.currentTimeMillis()
        val timeSinceLastMotion = now - lastMotionTime
        val avgHR = hrWindow.average().toFloat()

        if (restingHeartRate == 0f && (now - serviceStartTime) > 10 * 60 * 1000L) {
            restingHeartRate = avgHR
        }
        
        val isHRDropped = if (restingHeartRate > 0) avgHR < (restingHeartRate * 0.94f) else avgHR < 75f
        val isDeepStationary = timeSinceLastMotion > 8 * 60 * 1000L
        
        if (isDeepStationary && isHRDropped) {
            motionSilenceCount++
            if (motionSilenceCount >= 3) {
                confirmSleep("Advanced Bio-Fusion Algorithm")
            }
        } else {
            motionSilenceCount = 0
        }
    }

    private fun triggerAlarmNow() {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.smartsleep.alarm.ACTION_ALARM"
        }
        sendBroadcast(intent)
        stopSelf()
    }

    private fun scheduleAlarmImmediate(targetTime: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        wakeUpTime = targetTime
        
        Log.d("SleepMonitor", "Scheduling IMMEDIATE alarm for: ${java.util.Date(wakeUpTime)}")

        val receiverIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.smartsleep.alarm.ACTION_ALARM"
        }
        val receiverPendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, receiverIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(wakeUpTime, receiverPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, receiverPendingIntent)
        } catch (e: Exception) {
            Log.e("SleepMonitor", "Failed to set immediate alarm", e)
        }
    }

    private var wakeUpTime: Long = 0
    private var isSmartWindowActive = false
    private var smartWindowJob: kotlinx.coroutines.Job? = null

    private fun scheduleAlarm(targetTime: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // ضمان أن المنبه دائماً في المستقبل (دقيقتين كحد أدنى)
        val safeTargetTime = targetTime.coerceAtLeast(System.currentTimeMillis() + 2 * 60 * 1000L)
        wakeUpTime = safeTargetTime
        
        Log.i("SleepMonitor", "⏰ MASTER ALARM set for: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(wakeUpTime))}")

        val receiverIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.smartsleep.alarm.ACTION_ALARM"
        }
        
        val receiverPendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, receiverIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // 1. للرؤية في النظام (أيقونة المنبه)
            val alarmClockInfo = AlarmManager.AlarmClockInfo(wakeUpTime, receiverPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, receiverPendingIntent)
            
            // تحديث النافذة الذكية
            smartWindowJob?.cancel()
            smartWindowJob = serviceScope.launch {
                val delayTime = (wakeUpTime - System.currentTimeMillis() - 20 * 60 * 1000L).coerceAtLeast(0)
                kotlinx.coroutines.delay(delayTime)
                isSmartWindowActive = true
                updateNotification("DreamPulse: Smart window active.")
            }
            Log.d("SleepMonitor", "Alarm secured successfully.")
        } catch (e: Exception) {
            Log.e("SleepMonitor", "CRITICAL: Failed to set alarm", e)
        }
    }

    private fun updateNotification(text: String) {
        val notification = notificationHelper.getTrackingNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun scheduleKeepAlive() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, com.smartsleep.alarm.receiver.BootReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 2002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // إعادة التشغيل كل 20 دقيقة كإجراء احترازي
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 20 * 60 * 1000L,
            20 * 60 * 1000L,
            pendingIntent
        )
    }

    private fun cancelKeepAlive() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, com.smartsleep.alarm.receiver.BootReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 2002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, com.smartsleep.alarm.receiver.AlarmReceiver::class.java)
        
        // إلغاء المنبه الأساسي
        val p1 = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p1)

        // إلغاء المنبه الاحتياطي
        val p2 = PendingIntent.getBroadcast(this, 1002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(p2)
        
        Log.d("SleepMonitor", "All alarms cancelled.")
    }

    override fun onDestroy() {
        // لا نلغي المنبه هنا! المنبه يجب أن يظل فعالاً في النظام حتى لو قُتلت الخدمة.
        cancelKeepAlive() // نوقف منبه "نبض الحياة" فقط عند الإغلاق اليدوي المتعمد
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d("SleepMonitor", "WakeLock released")
        }
        sensorManager?.unregisterListener(this)
        sleepRepository.stopMonitoring()
        healthServicesManager.setSimulation(false)
        healthServicesManager.updateOnBodyStatus(false)
        healthServicesManager.updateHeartRate(0f)
        hrWindow.clear()
        motionWindow.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SLEEP_DURATION = "extra_sleep_duration"
        const val ACTION_STOP_MONITORING = "com.smartsleep.alarm.ACTION_STOP_MONITORING"
        const val ACTION_SIMULATE_SLEEP = "com.smartsleep.alarm.ACTION_SIMULATE_SLEEP"
        private const val ALARM_REQUEST_CODE = 1001
    }
}
