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
    
    // متغيرات تتبع النوم المتقدمة (النسخة الآمنة)
    private var serviceStartTime: Long = 0
    private val hrWindow = mutableListOf<Float>()
    private val motionWindow = mutableListOf<Float>()
    private var lastMotionTime: Long = System.currentTimeMillis()
    private var isSleepConfirmed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SleepMonitor", "Service Starting (Ultra-Reliability Mode)...")
        serviceStartTime = System.currentTimeMillis()
        sleepDurationMillis = intent?.getLongExtra(EXTRA_SLEEP_DURATION, 8 * 3600 * 1000L) ?: 0
        
        val notificationText = "DreamPulse: Initializing smart monitoring..."
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notificationHelper.getTrackingNotification(notificationText)
        )

        setupSensors()
        observeSleepState()
        sleepRepository.startMonitoring()
        
        // --- منبه الأمان (Fail-Safe) ---
        // نقوم بضبط منبه احتياطي فوراً للوقت الأقصى (المدة + ساعة إضافية)
        // لضمان الاستيقاظ حتى لو فشل اكتشاف النوم
        val fallbackTime = System.currentTimeMillis() + sleepDurationMillis + (60 * 60 * 1000L)
        scheduleAlarm(fallbackTime)
        
        updateNotification("DreamPulse: Safe-Monitoring Active (Alarm Guarded)")
        
        serviceScope.launch(Dispatchers.Main) {
            val isSim = healthServicesManager.isSimulation.value
            if (isSim) updateNotification("SIMULATION STARTED: Waiting for sleep signal...")
        }

        return START_STICKY
    }

    private fun observeSleepState() {
        serviceScope.launch {
            healthServicesManager.sleepState.collectLatest { state ->
                Log.d("SleepMonitor", "System Sleep State: $state")
                if (state == SleepState.ASLEEP && !isSleepConfirmed) {
                    val isSim = healthServicesManager.isSimulation.value
                    serviceScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@SleepMonitorService, "System Signal: Sleep Detected", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    confirmSleep("System Signal", isSimulation = isSim)
                }
            }
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val hr = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        
        // استخدام SENSOR_DELAY_UI لدقة أفضل في تحليل الحركة
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, hr, SensorManager.SENSOR_DELAY_UI)
    }

    private fun confirmSleep(source: String, isSimulation: Boolean = false) {
        if (isSleepConfirmed) return
        
        // صمام أمان: لا يمكن تأكيد النوم في أول 5 دقائق من تشغيل الخدمة (إلا في وضع المحاكاة)
        // تم تقليلها من 10 دقائق لتسريع الاكتشاف في القيلولة القصيرة
        if (!isSimulation && System.currentTimeMillis() - serviceStartTime < 5 * 60 * 1000L) {
            Log.d("SleepMonitor", "Sleep detected too early ($source), ignoring for safety.")
            return
        }

        isSleepConfirmed = true
        Log.i("SleepMonitor", "🎉 True Sleep Confirmed via $source (Simulation: $isSimulation)")
        val now = System.currentTimeMillis()
        val startTime = now

        serviceScope.launch {
            sleepRepository.saveSleepStartTime(startTime)
        }
        
        // 1. تقليل استهلاك الطاقة: إيقاف حساس النبض بعد اكتشاف النوم
        val hrSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        sensorManager?.unregisterListener(this, hrSensor)
        
        // 2. تحديث المنبه للوقت الفعلي بدقة (بناءً على اختيار المستخدم)
        val targetWakeTime = now + sleepDurationMillis
        
        // في وضع المحاكاة أو الفترات القصيرة، نتجاوز الـ 30 دقيقة الاحتياطية
        if (isSimulation || sleepDurationMillis < 30 * 60 * 1000L) {
            scheduleAlarmImmediate(targetWakeTime)
        } else {
            scheduleAlarm(targetWakeTime)
        }
        
        serviceScope.launch(Dispatchers.Main) {
            val timeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(targetWakeTime))
            updateNotification("SLEEP DETECTED! Alarm set for $timeStr")
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
                
                // إضافة الحركة للنافذة (آخر 50 قراءة)
                motionWindow.add(absMag)
                if (motionWindow.size > 50) motionWindow.removeAt(0)

                if (absMag > 0.15f) { // خفض العتبة لزيادة الحساسية ومنع الإنذارات المبكرة
                    lastMotionTime = System.currentTimeMillis()
                    if (isSleepConfirmed && isSmartWindowActive) triggerAlarmNow()
                }
            }
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0]
                if (bpm > 30) {
                    // تحديث الحالة العامة للحساسات
                    healthServicesManager.updateHeartRate(bpm)
                    healthServicesManager.updateOnBodyStatus(true)
                    
                    hrWindow.add(bpm)
                    if (hrWindow.size > 20) hrWindow.removeAt(0)
                    checkAdvancedHeuristic()
                } else {
                    // إذا كان النبض صفر أو غير موجود (أثناء الشحن مثلاً)
                    healthServicesManager.updateOnBodyStatus(false)
                }
            }
        }
    }

    private fun checkAdvancedHeuristic() {
        if (isSleepConfirmed || hrWindow.size < 15) return
        
        val now = System.currentTimeMillis()
        val timeSinceLastMotion = now - lastMotionTime
        
        // 1. تحليل استقرار النبض (معيار سامسونج للدقة)
        val avgHR = hrWindow.average().toFloat()
        // استقرار ذكي: الاختلاف لا يتعدى 4 نبضات عن المتوسط (تم تعديلها لسرعة الاكتشاف)
        val isHRStable = hrWindow.all { it < (avgHR + 4) && it > (avgHR - 4) }
        
        // التأكد من أن النبض مستقر وفي مستواه الطبيعي للهناء (أقل من 80)
        val isLowHR = avgHR < 80f
        
        // 2. تحليل السكون التام (Actigraphy Clinical Standard)
        // SMA: Signal Magnitude Area - حساب متوسط الحركة في النافذة
        val avgMotion = if (motionWindow.isNotEmpty()) motionWindow.average().toFloat() else 1f
        
        // شرط السكون: 15 دقيقة من عدم الحركة مع متوسط حركة شبه معدوم
        val isDeepStationary = timeSinceLastMotion > 15 * 60 * 1000L && avgMotion < 0.03f
        
        // Decision: Sensor Fusion (Samsung-Grade)
        if (isDeepStationary && isHRStable && isLowHR) {
            serviceScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(this@SleepMonitorService, "Sensors: Deep Sleep Confirmed", android.widget.Toast.LENGTH_SHORT).show()
            }
            confirmSleep("Pro Sensor Fusion (Samsung-Grade)")
        }
    }

    private fun triggerAlarmNow() {
        val intent = Intent(this, AlarmReceiver::class.java)
        sendBroadcast(intent)
        stopSelf()
    }

    private fun scheduleAlarmImmediate(targetTime: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        wakeUpTime = targetTime
        
        Log.d("SleepMonitor", "Scheduling IMMEDIATE alarm for: ${java.util.Date(wakeUpTime)}")

        val receiverIntent = Intent(this, AlarmReceiver::class.java)
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
        
        // التأكد من أن وقت الاستيقاظ دائماً في المستقبل
        val safeTargetTime = targetTime.coerceAtLeast(System.currentTimeMillis() + 2 * 60 * 1000L)
        wakeUpTime = safeTargetTime
        
        Log.d("SleepMonitor", "Scheduling alarm for: ${java.util.Date(wakeUpTime)}")

        val receiverIntent = Intent(this, AlarmReceiver::class.java)
        val receiverPendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, receiverIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(wakeUpTime, receiverPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, receiverPendingIntent)
            
            // تحديث النافذة الذكية: إلغاء التوقيت القديم وبدء توقيت جديد بناءً على الموعد الجديد
            smartWindowJob?.cancel()
            smartWindowJob = serviceScope.launch {
                val delayTime = (wakeUpTime - System.currentTimeMillis() - 20 * 60 * 1000L).coerceAtLeast(0)
                kotlinx.coroutines.delay(delayTime)
                isSmartWindowActive = true
                updateNotification("DreamPulse: Smart window active.")
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

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, com.smartsleep.alarm.receiver.AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("SleepMonitor", "Alarm cancelled.")
    }

    override fun onDestroy() {
        cancelAlarm() // إلغاء المنبه عند إيقاف الخدمة يدوياً
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
        private const val ALARM_REQUEST_CODE = 1001
    }
}
