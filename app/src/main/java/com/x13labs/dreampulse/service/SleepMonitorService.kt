package com.x13labs.dreampulse.service

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
import com.x13labs.dreampulse.data.repository.SleepRepository
import com.x13labs.dreampulse.domain.model.SleepState
import com.x13labs.dreampulse.receiver.AlarmReceiver
import com.x13labs.dreampulse.receiver.HeartbeatReceiver
import com.x13labs.dreampulse.util.NotificationHelper
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
    @Inject lateinit var healthServicesManager: com.x13labs.dreampulse.data.sensors.HealthServicesManager
    @Inject lateinit var preferencesManager: com.x13labs.dreampulse.data.local.PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sleepDurationMillis: Long = 0
    private var sensorManager: SensorManager? = null
    
    // Bug #5 fix: Single WakeLock instance, refreshed in duty cycle
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var serviceStartTime: Long = 0
    private val hrWindow = mutableListOf<Float>()
    private var hrBaseline = 0f
    @Volatile private var lastMotionTime: Long = System.currentTimeMillis()

    // On-wrist state from TYPE_LOW_LATENCY_OFFBODY_DETECT: null until the sensor reports
    // (or if the watch has no such sensor), then true/false.
    @Volatile private var isOnBody: Boolean? = null
    @Volatile private var lastHeartRateTime = 0L
    @Volatile private var isSleepConfirmed = false
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
            HeartbeatReceiver.cancel(this)
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

        val initialNotification = notificationHelper.buildTracking(
            getString(com.x13labs.dreampulse.R.string.notif_monitoring),
            getString(com.x13labs.dreampulse.R.string.notif_monitoring_text),
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                initialNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)
        }

        val isFreshStart = intent?.getBooleanExtra(EXTRA_FRESH_START, false) ?: false

        // Heartbeat ping to an instance that is already running: startForeground above is all
        // that is needed. Re-reading persisted state here could overwrite in-memory state that
        // has not been written to DataStore yet (e.g. a sleep confirmation in progress).
        val opensSmartWindow = action == ACTION_SMART_WINDOW
        if (isInitialized && !isFreshStart) {
            if (opensSmartWindow) openSmartWindow()
            return START_STICKY
        }

        serviceScope.launch {
            val savedStartTime = preferencesManager.serviceStartTime.first()

            if (isFreshStart) {
                // Fresh start from UI
                sleepDurationMillis = intent?.getLongExtra(EXTRA_SLEEP_DURATION, 0L)?.takeIf { it > 0 }
                    ?: (preferencesManager.sleepDuration.first() * 60 * 1000L)
                serviceStartTime = System.currentTimeMillis()
                isSleepConfirmed = false
                preferencesManager.saveServiceStartTime(serviceStartTime)
                preferencesManager.saveSleepConfirmed(false)
                preferencesManager.setTrackingActive(true)
                // A sleep start left over from the previous night must not leak into this one
                sleepRepository.saveSleepStartTime(0L)
                healthServicesManager.resetStates()
                lastMotionTime = System.currentTimeMillis()

                // Safe Mode: schedule a backup alarm right now, so the user still wakes up even
                // if sleep is never detected or this service is killed and never comes back.
                // Budget = requested duration + maximum sleep latency; the Hard Deadline wins
                // if it is earlier. confirmSleep() later replaces it with the real wake time.
                var backupTime = serviceStartTime + sleepDurationMillis + MAX_SLEEP_LATENCY_MS
                hardDeadlineAfter(serviceStartTime)?.let { if (it < backupTime) backupTime = it }
                Log.d("SleepMonitor", "Backup alarm: ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(backupTime))}")
                preferencesManager.saveTargetWakeTime(backupTime)
                scheduleAlarmsInternal(backupTime)
            } else if (savedStartTime > 0) {
                // Recovery: sticky restart by the system, heartbeat, or boot.
                // The intent may be null here, so everything comes from DataStore.
                val targetWakeTime = preferencesManager.targetWakeTime.first()
                if (targetWakeTime > 0 && System.currentTimeMillis() - targetWakeTime > STALE_SESSION_MS) {
                    Log.w("SleepMonitor", "Session ended long ago — clearing instead of re-arming the alarm")
                    clearSession()
                    stopSelf()
                    return@launch
                }
                sleepDurationMillis = preferencesManager.sleepDuration.first() * 60 * 1000L
                serviceStartTime = savedStartTime
                isSleepConfirmed = preferencesManager.isSleepConfirmed.first()
                sleepRepository.setTracking(true)

                // Alarms do not survive a reboot — re-arm whatever was scheduled (backup or real).
                if (targetWakeTime > 0) {
                    scheduleAlarmsInternal(targetWakeTime)
                }
            } else {
                // Restarted with no session to resume (e.g. sticky restart after it ended)
                Log.d("SleepMonitor", "No active session — stopping")
                stopSelf()
                return@launch
            }

            HeartbeatReceiver.schedule(this@SleepMonitorService)
            com.x13labs.dreampulse.tile.SleepTileService.requestUpdate(this@SleepMonitorService)

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

            // The smart-window alarm revived a killed service: recovery above restored the
            // session, now open the window.
            if (opensSmartWindow) openSmartWindow()
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
                    lastHeartRateTime = System.currentTimeMillis()
                    synchronized(hrWindow) {
                        hrWindow.add(bpm)
                        if (hrWindow.size > 30) hrWindow.removeAt(0)
                    }
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
        // Wake-up, on-change sensor: reports the current state on registration, then only
        // when the watch is put on / taken off, so it costs next to nothing.
        sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * A watch lying still on a table looks exactly like a sleeper to the motion heuristic,
     * so detection only counts while the watch is worn. Prefer the off-body sensor; if it has
     * not reported (or does not exist), fall back to "a heart rate was read recently" — the
     * duty cycle measures every 5 minutes, and no pulse is read off-wrist.
     */
    private fun isWorn(now: Long): Boolean = when (isOnBody) {
        true -> true
        false -> false
        null -> now - lastHeartRateTime < HR_FRESHNESS_MS
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
            val deadlineTimestamp = hardDeadlineAfter(serviceStartTime)
            if (deadlineTimestamp != null) {
                if (deadlineTimestamp < targetWakeTime) {
                    targetWakeTime = deadlineTimestamp
                    Log.d("SleepMonitor", "Hard deadline active — waking at ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(deadlineTimestamp))} instead of ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(baseTargetWakeTime))}")
                }
            }
            
            preferencesManager.saveTargetWakeTime(targetWakeTime)
            
            val timeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(targetWakeTime))
            val confirmTimeStr = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(now))
            sleepRepository.saveSleepSummary("$confirmTimeStr, $timeStr")
            
            val notification = notificationHelper.buildTracking(
                getString(com.x13labs.dreampulse.R.string.notif_alarm_set, timeStr),
                getString(com.x13labs.dreampulse.R.string.notif_alarm_set_text, timeStr),
            )
            com.x13labs.dreampulse.tile.SleepTileService.requestUpdate(this@SleepMonitorService)
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
        if (event?.sensor?.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val onBody = event.values[0] == 1.0f
            if (onBody != isOnBody) Log.d("SleepMonitor", if (onBody) "Watch ON wrist" else "Watch OFF wrist — sleep detection paused")
            isOnBody = onBody
            healthServicesManager.updateOnBodyStatus(onBody)
            // Putting the watch back on restarts the stillness timer from zero
            if (onBody) lastMotionTime = System.currentTimeMillis()
            return
        }
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

        // Not worn: pause detection by holding the stillness timer at zero. Only sleep while
        // wearing the watch counts. (The backup alarm still covers the whole night.)
        if (!isWorn(now)) {
            lastMotionTime = now
            return
        }

        val timeSinceLastMotion = now - lastMotionTime
        val currentAvg = synchronized(hrWindow) { if (hrWindow.isNotEmpty()) hrWindow.average().toFloat() else 0f }
        
        if (hrBaseline == 0f && currentAvg > 0f && (now - serviceStartTime > 5 * 60 * 1000L)) {
            hrBaseline = currentAvg
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
            action = "com.x13labs.dreampulse.ACTION_ALARM"
        }
        sendBroadcast(intent)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 2000)
    }

    @Volatile private var isSmartWindowActive = false
    private var smartWakeLock: PowerManager.WakeLock? = null

    private fun scheduleAlarmsInternal(targetTime: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val safeTargetTime = targetTime.coerceAtLeast(System.currentTimeMillis() + 60 * 1000L)
        
        Log.d("SleepMonitor", "Scheduling alarm: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(safeTargetTime))}")
        
        val receiverIntent = Intent(this, AlarmReceiver::class.java).apply { action = "com.x13labs.dreampulse.ACTION_ALARM" }
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, receiverIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(safeTargetTime, pendingIntent), pendingIntent)

            // Smart Wake: Only activate for durations >= 30 minutes.
            // For short durations (testing/SM), skip smart wake — the alarm fires on its own.
            //
            // The window is opened by an exact alarm, not a coroutine delay(): during the night
            // the CPU deep-sleeps, the monotonic clock behind delay() stops, and the window
            // would open late or never. The alarm also revives the service if it was killed.
            isSmartWindowActive = false
            smartWindowTime = safeTargetTime
            val smartWindowIntent = smartWindowPendingIntent()
            alarmManager.cancel(smartWindowIntent)
            val timeUntilAlarm = safeTargetTime - System.currentTimeMillis()
            if (timeUntilAlarm >= 30 * 60 * 1000L) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    safeTargetTime - SMART_WINDOW_MS,
                    smartWindowIntent
                )
            } else {
                Log.d("SleepMonitor", "Duration < 30min — Smart Wake disabled, alarm will fire at scheduled time")
            }
        } catch (e: Exception) {
            Log.e("SleepMonitor", "Failed to schedule alarm", e)
        }
    }

    private var smartWindowTime = 0L

    private fun smartWindowPendingIntent(): PendingIntent {
        val intent = Intent(this, SleepMonitorService::class.java).apply { action = ACTION_SMART_WINDOW }
        return PendingIntent.getForegroundService(
            this, SMART_WINDOW_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Last [SMART_WINDOW_MS] before the alarm: keep the CPU awake and listen to the
     * accelerometer, so a light-sleep movement triggers the alarm early.
     */
    private fun openSmartWindow() {
        if (!isSleepConfirmed || isSmartWindowActive) return
        val remaining = smartWindowTime - System.currentTimeMillis()
        if (remaining <= 0) return
        Log.d("SleepMonitor", "Smart wake window ACTIVE (${remaining / 60000} min left)")

        // Hold the CPU until just after the scheduled alarm; without it the (non-wakeup)
        // accelerometer stops delivering events as soon as the watch dozes.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        smartWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamPulse:SmartWake").apply {
            setReferenceCounted(false)
            acquire(remaining + 60 * 1000L)
        }
        // A revived instance never registered the accelerometer (it skips setup when sleep is
        // already confirmed); re-register in every case.
        sensorManager = sensorManager ?: getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.unregisterListener(this)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        motionEvents.clear()
        isSmartWindowActive = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(smartWindowPendingIntent())
        isSmartWindowActive = false
        val intent = Intent(this, AlarmReceiver::class.java).apply { action = "com.x13labs.dreampulse.ACTION_ALARM" }
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Next occurrence of the user's Hard Deadline after [from], or null if it is disabled.
     * Anchored on the session start (not "now") so a confirmation after midnight still
     * resolves to the same morning instead of jumping a day ahead.
     */
    private suspend fun hardDeadlineAfter(from: Long): Long? {
        if (!preferencesManager.hardDeadlineEnabled.first()) return null
        val deadlineMinutes = preferencesManager.hardDeadlineMinutes.first()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = from
            set(java.util.Calendar.HOUR_OF_DAY, deadlineMinutes / 60)
            set(java.util.Calendar.MINUTE, deadlineMinutes % 60)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= from) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private suspend fun clearSession() {
        HeartbeatReceiver.cancel(this)
        sleepRepository.setTracking(false)
        preferencesManager.setTrackingActive(false)
        preferencesManager.saveSleepConfirmed(false)
        preferencesManager.saveServiceStartTime(0L)
        preferencesManager.saveTargetWakeTime(0L)
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
        // This allows HeartbeatReceiver to revive the service if the system killed it.
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (smartWakeLock?.isHeld == true) {
            smartWakeLock?.release()
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
        const val ACTION_STOP_MONITORING = "com.x13labs.dreampulse.ACTION_STOP_MONITORING"
        const val ACTION_SMART_WINDOW = "com.x13labs.dreampulse.ACTION_SMART_WINDOW"
        const val ACTION_SIMULATE_SLEEP = "com.x13labs.dreampulse.ACTION_SIMULATE_SLEEP"
        private const val ALARM_REQUEST_CODE = 1001
        private const val SMART_WINDOW_REQUEST_CODE = 1004
        private const val SMART_WINDOW_MS = 15 * 60 * 1000L
        /** Longer than one HR duty cycle (90 s on / 3.5 min off). */
        private const val HR_FRESHNESS_MS = 6 * 60 * 1000L
        /** Longest we expect falling asleep to take; also used by the heuristic's catch-all. */
        private const val MAX_SLEEP_LATENCY_MS = 60 * 60 * 1000L
        /** A recovered session whose wake time passed longer ago than this is considered over. */
        private const val STALE_SESSION_MS = 2 * 60 * 60 * 1000L
    }
}
