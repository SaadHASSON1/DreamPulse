package com.x13labs.dreampulse.ui.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x13labs.dreampulse.data.repository.SleepRepository
import com.x13labs.dreampulse.domain.model.SleepState
import com.x13labs.dreampulse.service.SleepMonitorService
import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.data.sensors.HealthServicesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepRepository: SleepRepository,
    private val preferencesManager: PreferencesManager,
    private val healthServicesManager: HealthServicesManager
) : ViewModel() {

    val isTrackingState: StateFlow<Boolean> = sleepRepository.isTracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _sleepDurationMinutes = MutableStateFlow(480)
    val sleepDurationMinutes: StateFlow<Int> = _sleepDurationMinutes

    // Activity listens to this to run its permission check flow
    private val _needsPermissionCheck = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val needsPermissionCheck = _needsPermissionCheck.asSharedFlow()

    private val _currentSleepState = MutableStateFlow(SleepState.UNKNOWN)
    val currentSleepState: StateFlow<SleepState> = _currentSleepState
    
    val lastSleepSummary: StateFlow<String> = sleepRepository.lastSleepSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No data available")

    private val _currentMotion = MutableStateFlow(0f)
    val currentMotion: StateFlow<Float> = _currentMotion

    private val _sleepStartTime = MutableStateFlow(0L)
    val sleepStartTime: StateFlow<Long> = _sleepStartTime
    
    val isOnBody: StateFlow<Boolean> = healthServicesManager.isOnBody

    private val _currentHeartRate = MutableStateFlow(0f)
    val currentHeartRate: StateFlow<Float> = _currentHeartRate

    /** What the tracking screen shows; read straight from the persisted session. */
    data class TrackingInfo(
        val sleepConfirmed: Boolean = false,
        val sleepStartTime: Long = 0L,
        val targetWakeTime: Long = 0L,
    )

    val trackingInfo: StateFlow<TrackingInfo> = kotlinx.coroutines.flow.combine(
        preferencesManager.isSleepConfirmed,
        preferencesManager.sleepStartTime,
        preferencesManager.targetWakeTime,
    ) { confirmed, sleepStart, target -> TrackingInfo(confirmed, sleepStart, target) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackingInfo())

    // Hard Deadline ("Must wake by" feature)
    private val _hardDeadlineEnabled = MutableStateFlow(false)
    val hardDeadlineEnabled: StateFlow<Boolean> = _hardDeadlineEnabled

    private val _hardDeadlineMinutes = MutableStateFlow(420) // 07:00 AM
    val hardDeadlineMinutes: StateFlow<Int> = _hardDeadlineMinutes

    init {
        // The in-memory tracking flag is lost when the process dies; restore it from the
        // persisted session so reopening the app shows the monitoring screen, not START.
        viewModelScope.launch {
            if (preferencesManager.isTrackingActive.first() && preferencesManager.serviceStartTime.first() > 0) {
                healthServicesManager.setTracking(true)
            }
        }
        viewModelScope.launch {
            preferencesManager.sleepDuration.collectLatest {
                _sleepDurationMinutes.value = it
            }
        }
        viewModelScope.launch {
            sleepRepository.sleepState.collectLatest {
                _currentSleepState.value = it
            }
        }
        viewModelScope.launch {
            sleepRepository.sleepStartTime.collectLatest {
                _sleepStartTime.value = it
            }
        }
        viewModelScope.launch {
            sleepRepository.heartRate.collectLatest { bpm ->
                if (bpm > 0) {
                    _currentHeartRate.value = bpm
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.hardDeadlineEnabled.collectLatest {
                _hardDeadlineEnabled.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.hardDeadlineMinutes.collectLatest {
                _hardDeadlineMinutes.value = it
            }
        }
    }

    fun setDuration(minutes: Int) {
        _sleepDurationMinutes.value = minutes
        viewModelScope.launch {
            preferencesManager.saveSleepDuration(minutes)
        }
    }

    /**
     * Called by the UI button. Emits a permission-check request to the Activity.
     * If all permissions pass, the Activity calls executeStartTracking().
     */
    fun startTracking() {
        if (isTrackingState.value) return
        Log.d("MainViewModel", "startTracking() — requesting permission check")
        _needsPermissionCheck.tryEmit(Unit)
    }

    /**
     * Called ONLY by the Activity after it has confirmed all permissions are granted.
     */
    fun executeStartTracking() {
        if (isTrackingState.value) return
        Log.d("MainViewModel", "executeStartTracking() — launching service")

        // No state clearing here: the service's fresh-start path resets every session field
        // itself. Clearing from here raced with it and could wipe the backup wake time the
        // service had just saved (then a reboot would not re-arm the alarm).

        // Vibration feedback
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(150)
        }

        val intent = Intent(context, SleepMonitorService::class.java).apply {
            putExtra(SleepMonitorService.EXTRA_SLEEP_DURATION, _sleepDurationMinutes.value * 60 * 1000L)
            putExtra(SleepMonitorService.EXTRA_FRESH_START, true)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            sleepRepository.setTracking(true)
            // NOTE: Don't call startMonitoring() here — the Service handles it in onStartCommand()
            Log.d("MainViewModel", "Service started — tracking = true")
            // The service schedules its own HeartbeatReceiver. Drop the periodic work left
            // behind by older versions (its worker class no longer exists).
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("SleepHeartbeat")
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start service", e)
        }
    }

    fun stopTracking() {
        val intent = Intent(context, SleepMonitorService::class.java).apply {
            action = SleepMonitorService.ACTION_STOP_MONITORING
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        sleepRepository.setTracking(false)
        healthServicesManager.updateSleepState(SleepState.UNKNOWN)
        healthServicesManager.setSimulation(false)
        com.x13labs.dreampulse.receiver.HeartbeatReceiver.cancel(context)
        com.x13labs.dreampulse.tile.SleepTileService.requestUpdate(context)
        
        viewModelScope.launch {
            preferencesManager.saveSleepConfirmed(false)
            preferencesManager.saveServiceStartTime(0L)
            preferencesManager.saveTargetWakeTime(0L)
        }
        
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.cancel()
    }

    fun simulateSleep() {
        healthServicesManager.setSimulation(true)
    }

    fun resetSleepSummary() {
        viewModelScope.launch {
            sleepRepository.saveSleepStartTime(0L)
        }
    }

    fun updateMotion(magnitude: Float) {
        if (Math.abs(_currentMotion.value - magnitude) > 0.05f) {
            _currentMotion.value = magnitude
        }
    }

    fun updateOnBodyStatus(isOnBody: Boolean) {
        healthServicesManager.updateOnBodyStatus(isOnBody)
    }

    fun updateHeartRate(bpm: Float) {
        if (_currentHeartRate.value.toInt() != bpm.toInt()) {
            _currentHeartRate.value = bpm
        }
    }

    fun saveName(name: String) {
        viewModelScope.launch {
            preferencesManager.saveUserName(name)
        }
    }

    fun toggleHardDeadline() {
        val newValue = !_hardDeadlineEnabled.value
        _hardDeadlineEnabled.value = newValue
        viewModelScope.launch {
            preferencesManager.saveHardDeadlineEnabled(newValue)
        }
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

    fun adjustHardDeadline(deltaMinutes: Int) {
        val newValue = (_hardDeadlineMinutes.value + deltaMinutes).coerceIn(0, 1439)
        _hardDeadlineMinutes.value = newValue
        viewModelScope.launch {
            preferencesManager.saveHardDeadlineMinutes(newValue)
        }
    }
}
