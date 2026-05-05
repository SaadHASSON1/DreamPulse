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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class SleepMonitorService : Service(), SensorEventListener {

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var healthServicesManager: com.smartsleep.alarm.data.sensors.HealthServicesManager
    @Inject lateinit var preferencesManager: com.smartsleep.alarm.data.local.PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sleepDurationMillis: Long = 0
    private var sensorManager: SensorManager? = null
    
    // Bug #5 fix: Single WakeLock instance, refreshed in duty cycle
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var serviceStartTime: Long = 0
    private val hrWindow = mutableListOf<Float>()
    private var hrBaseline = 0f
    private var lastMotionTime: Long = System.currentTimeMillis()
    private var isSleepConfirmed = false
    private var motionSilenceCount = 0
    private var motionEvents = mutableListOf<Long>()
    private var isInitialized = false
    
    // Flag to distinguish system kill from user stop
    private var stoppedByUser = false

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            healthServicesManager.isSimulation.collectLatest { active ->
                if (active && !isSleepConfirmed) {
                    confirmSleep("Live Simulation", isSimulation = true)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == ACTION_STOP_MONITORING) {
            stoppedByUser = true
            sensorManager?.unregisterListener(this)
            healthServicesManager.stopPassiveSleepMonitoring()
            cancelAlarm()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = NotificationCompat.Builder(this, NotificationHelper.TRACKING_CHANNEL_ID)
            .setSmallIcon(com.smartsleep.alarm.R.mipmap.ic_launcher)
            .setContentTitle("DreamPulse: Monitoring")
            .setContentText("Signals active. Rest well 🌙")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                initialNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
        }

        sleepDurationMillis = intent?.getLongExtra(EXTRA_SLEEP_DURATION, 480 * 60 * 1000L) ?: 480 * 60 * 1000L
        val isFreshStart = intent?.getBooleanExtra(EXTRA_FRESH_START, false) ?: false

        serviceScope.launch {
            val savedConfirmed = preferencesManager.isSleepConfirmed.first()
            val savedStartTime = preferencesManager.serviceStartTime.first()
            val targetWakeTime = preferencesManager.targetWakeTime.first()

            if (!isFreshStart && savedStartTime > 0) {
                // Recovery or Heartbeat Ping
                serviceStartTime = savedStartTime
                isSleepConfirmed = savedConfirmed
                sleepRepository.setTracking(true)
                
                if (savedConfirmed) {
                    scheduleAlarmsInternal(targetWakeTime)
                }
            } else {
                // Fresh start from UI
                serviceStartTime = System.currentTimeMillis()
                isSleepConfirmed = false
                preferencesManager.saveServiceStartTime(serviceStartTime)
                preferencesManager.saveSleepConfirmed(false)
                healthServicesManager.resetStates()
                lastMotionTime = System.currentTimeMillis()
            }
            
            if (!isInitialized) {
                isInitialized = true
                try {
                    if (!isSleepConfirmed) {
                        setupSensors()
                        observeSleepState()
                        observeHeartRate()
                        delay(500)
                        healthServicesManager.startHeartRateMeasurement()
                        startHeartRateDutyCycle() 
                        healthServicesManager.startPassiveSleepMonitoring()
                        startInternalHeuristicLoop()
                    }
                    
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@SleepMonitorService, "Sleep Tracking Active!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("SleepMonitor", "Start failed", e)
                }
            }
        }

        return START_STICKY
    }

    private fun startInternalHeuristicLoop() {
        serviceScope.launch {
            while (!isSleepConfirmed) {
                checkAdvancedHeuristic()
                delay(20000) // check every 20s
            }
        }
    }

    private fun observeHeartRate() {
        serviceScope.launch {
            healthServicesManager.heartRate.collectLatest { bpm ->
                if (bpm > 30) {
                    hrWindow.add(bpm)
                    if (hrWindow.size > 30) hrWindow.removeAt(0)
                }
            }
        }
    }

    private fun observeSleepState() {
        serviceScope.launch {
            healthServicesManager.sleepState.collectLatest { state ->
                if (state == SleepState.ASLEEP && !isSleepConfirmed) {
                    confirmSleep("System Health Provider")
                }
            }
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun confirmSleep(source: String, isSimulation: Boolean = false) {
        if (isSleepConfirmed) return
        
        // Safety: Prevent accidental trigger in the first minute unless simulation
        if (!isSimulation && System.currentTimeMillis() - serviceStartTime < 60000) return

        Log.d("SleepMonitor", "Sleep confirmed via: $source (simulation=$isSimulation, duration=${sleepDurationMillis}ms)")
        isSleepConfirmed = true
        healthServicesManager.updateSleepState(SleepState.ASLEEP)
        val now = System.currentTimeMillis()
        
        serviceScope.launch {
            preferencesManager.saveSleepConfirmed(true)
            sleepRepository.saveSleepStartTime(now)
            sleepRepository.setTracking(true)
            healthServicesManager.stopHeartRateMeasurement()
        }
        
        // Stop battery-heavy passive monitoring and HR measurement,
        // but KEEP the accelerometer registered for Smart Wake window detection.
        // The accelerometer uses minimal power and is needed to detect user movement
        // in the 15-minute window before the alarm to trigger early wake.
        try {
            healthServicesManager.stopPassiveSleepMonitoring()
        } catch (e: Exception) { }
        
        // SM fix: Always use the user-configured duration, even for simulation
        val baseTargetWakeTime = now + sleepDurationMillis
        
        serviceScope.launch {
            // Hard Deadline: Use whichever comes first — duration-based or deadline
            var targetWakeTime = baseTargetWakeTime
            val deadlineEnabled = preferencesManager.hardDeadlineEnabled.first()
            if (deadlineEnabled) {
                val deadlineMinutes = preferencesManager.hardDeadlineMinutes.first()
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, deadlineMinutes / 60)
                    set(java.util.Calendar.MINUTE, deadlineMinutes % 60)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                // If deadline already passed today, use tomorrow
                if (cal.timeInMillis <= now) {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                val deadlineTimestamp = cal.timeInMillis
                if (deadlineTimestamp < targetWakeTime) {
                    targetWakeTime = deadlineTimestamp
                    Log.d("SleepMonitor", "Hard deadline active — waking at ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(deadlineTimestamp))} instead of ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(baseTargetWakeTime))}")
                }
            }
            
            preferencesManager.saveTargetWakeTime(targetWakeTime)
            
            val timeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(targetWakeTime))
            val confirmTimeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(now))
            sleepRepository.saveSleepSummary("$confirmTimeStr, $timeStr")
            
            val notification = NotificationCompat.Builder(this@SleepMonitorService, NotificationHelper.TRACKING_CHANNEL_ID)
                .setSmallIcon(com.smartsleep.alarm.R.mipmap.ic_launcher)
                .setContentTitle("DreamPulse: Alarm Set")
                .setContentText("Sleep detected. Waking you at $timeStr")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NotificationHelper.NOTIFICATION_ID, notification)
            
            // Schedule alarm (works for both simulation and real sleep)
            scheduleAlarmsInternal(targetWakeTime)
        }
        
        serviceScope.launch(Dispatchers.Main) {
            val durationMinutes = (sleepDurationMillis / 60000).toInt()
            val toastMsg = if (isSimulation) {
                "SM: Alarm in ${durationMinutes}m"
            } else {
                "Sleep Detected. Alarm Secure."
            }
            android.widget.Toast.makeText(this@SleepMonitorService, toastMsg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val magnitude = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]) - 9.81f
            val absMag = if (magnitude < 0) -magnitude else magnitude
            
            if (absMag > 0.4f) { 
                val now = System.currentTimeMillis()
                motionEvents.add(now)
                motionEvents.removeAll { now - it > 4000 } // Keep last 4 seconds
                if (motionEvents.size >= 3) {
                    lastMotionTime = now
                    motionSilenceCount = 0
                    if (isSleepConfirmed && isSmartWindowActive) triggerAlarmNow()
                    motionEvents.clear()
                }
            }
        }
    }

    private fun checkAdvancedHeuristic() {
        if (isSleepConfirmed) return
        
        val now = System.currentTimeMillis()
        val timeSinceLastMotion = now - lastMotionTime
        val currentAvg = if (hrWindow.isNotEmpty()) hrWindow.average().toFloat() else 0f
        
        if (hrBaseline == 0f && hrWindow.isNotEmpty() && (now - serviceStartTime > 5 * 60 * 1000L)) {
            hrBaseline = hrWindow.average().toFloat()
        }

        // 1. ABSOLUTE MOTION TIMEOUT: 10 minutes of NO movement
        if (timeSinceLastMotion > 10 * 60 * 1000L) {
            confirmSleep("Motion Timeout")
            return
        }

        // 2. HR TREND: Heart rate drops below baseline during stillness
        if (timeSinceLastMotion > 5 * 60 * 1000L && currentAvg > 0 && hrBaseline > 0 && currentAvg < (hrBaseline - 8f)) {
            confirmSleep("Resting HR Heuristic")
            return
        }
        
        // 3. FALLBACK: After 45 minutes, become very inclusive
        if ((now - serviceStartTime) > 45 * 60 * 1000L && timeSinceLastMotion > 5 * 60 * 1000L) {
             confirmSleep("Safety Catch-all")
        }
    }

    private fun triggerAlarmNow() {
        Log.d("SleepMonitor", "triggerAlarmNow() — cancelling scheduled alarm and sending broadcast")
        // Cancel the scheduled setAlarmClock to prevent double alarm
        cancelAlarm()
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.smartsleep.alarm.ACTION_ALARM"
        }
        sendBroadcast(intent)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 2000)
    }

    private var isSmartWindowActive = false
    private var smartWindowJob: kotlinx.coroutines.Job? = null

    private fun scheduleAlarmsInternal(targetTime: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val safeTargetTime = targetTime.coerceAtLeast(System.currentTimeMillis() + 60 * 1000L)
        
        Log.d("SleepMonitor", "Scheduling alarm: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(safeTargetTime))}")
        
        val receiverIntent = Intent(this, AlarmReceiver::class.java).apply { action = "com.smartsleep.alarm.ACTION_ALARM" }
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, receiverIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(safeTargetTime, pendingIntent), pendingIntent)

            // Smart Wake: Only activate for durations >= 30 minutes.
            // For short durations (testing/SM), skip smart wake — the alarm fires on its own.
            val timeUntilAlarm = safeTargetTime - System.currentTimeMillis()
            if (timeUntilAlarm >= 30 * 60 * 1000L) {
                smartWindowJob?.cancel()
                smartWindowJob = serviceScope.launch {
                    val delayTime = timeUntilAlarm - 15 * 60 * 1000L
                    delay(delayTime)
                    isSmartWindowActive = true
                    Log.d("SleepMonitor", "Smart wake window ACTIVE")
                }
            } else {
                Log.d("SleepMonitor", "Duration < 30min — Smart Wake disabled, alarm will fire at scheduled time")
            }
        } catch (e: Exception) {
            Log.e("SleepMonitor", "Failed to schedule alarm", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply { action = "com.smartsleep.alarm.ACTION_ALARM" }
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    private fun startHeartRateDutyCycle() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // Bug #5 fix: Create a single WakeLock instance
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamPulse:SleepMonitor")
        wakeLock?.setReferenceCounted(false)  // Prevent leak — single acquire/release
        
        serviceScope.launch {
            while (true) {
                // Refresh WakeLock (non-reference-counted, so re-acquire just extends)
                wakeLock?.acquire(15 * 60 * 1000L)

                if (isSleepConfirmed) {
                    healthServicesManager.stopHeartRateMeasurement()
                    break // Stop measure client entirely once sleep is confirmed
                } else {
                    healthServicesManager.startHeartRateMeasurement(forceRestart = false)
                    delay(90000) // 90 seconds continuous measure
                    healthServicesManager.stopHeartRateMeasurement()
                    delay(210000) // 3.5 minutes rest (5 min cycle)
                }
            }
        }
    }

    override fun onDestroy() {
        // Bug #6 fix: Only release resources, do NOT clear tracking state.
        // Tracking state should only be cleared by explicit user action (ACTION_STOP_MONITORING).
        // This allows HeartbeatWorker to revive the service if the system killed it.
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        sensorManager?.unregisterListener(this)
        healthServicesManager.setSimulation(false)
        
        // Only clear tracking state if the user explicitly stopped
        if (stoppedByUser) {
            sleepRepository.stopMonitoring()
        }
        
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val EXTRA_SLEEP_DURATION = "extra_sleep_duration"
        const val EXTRA_FRESH_START = "extra_fresh_start"
        const val ACTION_STOP_MONITORING = "com.smartsleep.alarm.ACTION_STOP_MONITORING"
        const val ACTION_SIMULATE_SLEEP = "com.smartsleep.alarm.ACTION_SIMULATE_SLEEP"
        private const val ALARM_REQUEST_CODE = 1001
    }
}
