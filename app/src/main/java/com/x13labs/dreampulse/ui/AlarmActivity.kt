package com.x13labs.dreampulse.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.data.repository.SleepRepository
import com.x13labs.dreampulse.service.AlarmService
import com.x13labs.dreampulse.service.SleepMonitorService
import com.x13labs.dreampulse.ui.screens.AlarmScreen
import com.x13labs.dreampulse.ui.screens.SleepSummary
import com.x13labs.dreampulse.ui.screens.SummaryScreen
import com.x13labs.dreampulse.ui.theme.DreamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class AlarmActivity : ComponentActivity(), SensorEventListener {

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var preferencesManager: PreferencesManager

    private val showSummary = mutableStateOf(false)
    private val summary = mutableStateOf(SleepSummary())
    private var sensorManager: SensorManager? = null
    private var lastShakeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        setContent {
            BackHandler { }
            DreamTheme {
                if (!showSummary.value) {
                    AlarmScreen(onDismiss = { dismissAlarm() })
                } else {
                    SummaryScreen(summary.value, onFinish = { stopAlarmAndFinish() })
                }
            }
        }
        handleAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    /**
     * "dismiss" comes from the Stop action on the alarm notification (also what a
     * double-pinch gesture triggers on watches that support it); "show_summary" skips
     * straight to the morning screen.
     */
    private fun handleAction(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_DISMISS -> dismissAlarm()
            ACTION_SHOW_SUMMARY -> { loadSummary(); showSummary.value = true }
        }
    }

    private fun loadSummary() {
        // Before the first unlock after a restart DataStore cannot be read: keep the empty summary
        if (!com.x13labs.dreampulse.data.local.BootAlarmStore.isUnlocked(this)) return
        lifecycleScope.launch {
            val sessionStart = preferencesManager.lastSessionStart.first()
            val sleepStart = preferencesManager.sleepStartTime.first()
            val scheduledWake = preferencesManager.lastScheduledWake.first()
            val wake = preferencesManager.lastWake.first().takeIf { it > 0 } ?: System.currentTimeMillis()
            // sleepStart belongs to this session only if it falls inside it
            val detected = sleepStart > 0 && sleepStart >= sessionStart && sleepStart < wake
            summary.value = SleepSummary(
                sleptMinutes = if (detected) ((wake - sleepStart) / 60_000).toInt() else null,
                latencyMinutes = if (detected && sessionStart > 0) ((sleepStart - sessionStart) / 60_000).toInt() else null,
                earlyMinutes = if (detected && scheduledWake > wake) ((scheduledWake - wake) / 60_000).toInt() else 0,
            )
        }
    }

    private fun dismissAlarm() {
        if (showSummary.value) return
        Log.d("AlarmActivity", "dismissAlarm() called")
        stopService(Intent(this, AlarmService::class.java))
        // The stop action (not stopService) also cancels any alarm still pending, so dismissing
        // can never leave the backup alarm armed. Before the first unlock after a restart the
        // service cannot run at all: the alarm that opened this screen has already rung then.
        try {
            startService(Intent(this, SleepMonitorService::class.java).setAction(SleepMonitorService.ACTION_STOP_MONITORING))
        } catch (e: Exception) {
            Log.w("AlarmActivity", "Could not stop the monitor service", e)
            stopService(Intent(this, SleepMonitorService::class.java))
        }
        sleepRepository.setTracking(false)
        // AlarmService already ended the session and saved it for the summary; clearing
        // again here is only a safety net in case the alarm service was killed first.
        if (com.x13labs.dreampulse.data.local.BootAlarmStore.isUnlocked(this)) lifecycleScope.launch {
            preferencesManager.saveSleepConfirmed(false)
            preferencesManager.saveServiceStartTime(0L)
            preferencesManager.saveTargetWakeTime(0L)
            preferencesManager.setTrackingActive(false)
        }
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
        )
        loadSummary()
        showSummary.value = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val v = event.values
        val acceleration = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) - 9.81f
        if (acceleration > 15f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 2000) {
                lastShakeTime = now
                if (!showSummary.value) dismissAlarm() else stopAlarmAndFinish()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopAlarmAndFinish() {
        stopService(Intent(this, AlarmService::class.java))
        stopService(Intent(this, SleepMonitorService::class.java))
        sleepRepository.setTracking(false)
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_SHOW_SUMMARY = "show_summary"
    }
}
