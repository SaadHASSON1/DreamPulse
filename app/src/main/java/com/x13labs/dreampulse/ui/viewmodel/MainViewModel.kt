package com.x13labs.dreampulse.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.data.repository.SleepRepository
import com.x13labs.dreampulse.data.sensors.HealthServicesManager
import com.x13labs.dreampulse.domain.model.SleepState
import com.x13labs.dreampulse.receiver.HeartbeatReceiver
import com.x13labs.dreampulse.service.SleepMonitorService
import com.x13labs.dreampulse.tile.SleepTileService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepRepository: SleepRepository,
    private val preferencesManager: PreferencesManager,
    private val healthServicesManager: HealthServicesManager,
) : ViewModel() {

    val isTrackingState: StateFlow<Boolean> = sleepRepository.isTracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _sleepDurationMinutes = MutableStateFlow(480)
    val sleepDurationMinutes: StateFlow<Int> = _sleepDurationMinutes

    /** The Activity listens to this to run its permission checks before starting. */
    private val _needsPermissionCheck = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val needsPermissionCheck = _needsPermissionCheck.asSharedFlow()

    val isOnBody: StateFlow<Boolean> = healthServicesManager.isOnBody

    /** What the tracking screen shows; read straight from the persisted session. */
    data class TrackingInfo(
        val sleepConfirmed: Boolean = false,
        val sleepStartTime: Long = 0L,
        val targetWakeTime: Long = 0L,
    )

    val trackingInfo: StateFlow<TrackingInfo> = combine(
        preferencesManager.isSleepConfirmed,
        preferencesManager.sleepStartTime,
        preferencesManager.targetWakeTime,
    ) { confirmed, sleepStart, target -> TrackingInfo(confirmed, sleepStart, target) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackingInfo())

    // Hard deadline ("wake by" time)
    private val _hardDeadlineEnabled = MutableStateFlow(false)
    val hardDeadlineEnabled: StateFlow<Boolean> = _hardDeadlineEnabled

    private val _hardDeadlineMinutes = MutableStateFlow(420) // 07:00
    val hardDeadlineMinutes: StateFlow<Int> = _hardDeadlineMinutes

    init {
        // The in-memory tracking flag is lost when the process dies; restore it from the
        // persisted session so reopening the app shows the tracking screen, not Start.
        viewModelScope.launch {
            if (preferencesManager.isTrackingActive.first() && preferencesManager.serviceStartTime.first() > 0) {
                healthServicesManager.setTracking(true)
            }
        }
        viewModelScope.launch { preferencesManager.sleepDuration.collectLatest { _sleepDurationMinutes.value = it } }
        viewModelScope.launch { preferencesManager.hardDeadlineEnabled.collectLatest { _hardDeadlineEnabled.value = it } }
        viewModelScope.launch { preferencesManager.hardDeadlineMinutes.collectLatest { _hardDeadlineMinutes.value = it } }
    }

    fun setDuration(minutes: Int) {
        _sleepDurationMinutes.value = minutes
        viewModelScope.launch { preferencesManager.saveSleepDuration(minutes) }
    }

    /** Called by the Start button: the Activity checks permissions, then calls [executeStartTracking]. */
    fun startTracking() {
        if (isTrackingState.value) return
        _needsPermissionCheck.tryEmit(Unit)
    }

    /** Called only by the Activity, once every permission is granted. */
    fun executeStartTracking() {
        if (isTrackingState.value) return
        // No state clearing here: the service's fresh-start path resets every session field
        // itself. Clearing from here raced with it and could wipe the backup wake time the
        // service had just saved (then a reboot would not re-arm the alarm).
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))

        val intent = Intent(context, SleepMonitorService::class.java).apply {
            putExtra(SleepMonitorService.EXTRA_SLEEP_DURATION, _sleepDurationMinutes.value * 60 * 1000L)
            putExtra(SleepMonitorService.EXTRA_FRESH_START, true)
        }
        try {
            context.startForegroundService(intent)
            sleepRepository.setTracking(true)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start service", e)
        }
    }

    fun stopTracking() {
        context.startForegroundService(
            Intent(context, SleepMonitorService::class.java).apply { action = SleepMonitorService.ACTION_STOP_MONITORING }
        )
        sleepRepository.setTracking(false)
        healthServicesManager.updateSleepState(SleepState.UNKNOWN)
        healthServicesManager.setSimulation(false)
        HeartbeatReceiver.cancel(context)
        SleepTileService.requestUpdate(context)
        viewModelScope.launch {
            preferencesManager.saveSleepConfirmed(false)
            preferencesManager.saveServiceStartTime(0L)
            preferencesManager.saveTargetWakeTime(0L)
        }
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    /** Debug builds only: pretend sleep was detected now. */
    fun simulateSleep() {
        healthServicesManager.setSimulation(true)
    }

    fun setHardDeadlineEnabled(enabled: Boolean) {
        _hardDeadlineEnabled.value = enabled
        viewModelScope.launch { preferencesManager.saveHardDeadlineEnabled(enabled) }
    }

    fun setHardDeadlineMinutes(minutes: Int) {
        val v = ((minutes % 1440) + 1440) % 1440
        _hardDeadlineMinutes.value = v
        viewModelScope.launch { preferencesManager.saveHardDeadlineMinutes(v) }
    }
}
