package com.smartsleep.alarm.ui.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsleep.alarm.data.repository.SleepRepository
import com.smartsleep.alarm.domain.model.SleepState
import com.smartsleep.alarm.service.SleepMonitorService
import com.smartsleep.alarm.data.local.PreferencesManager
import com.smartsleep.alarm.data.sensors.HealthServicesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepRepository: SleepRepository,
    private val preferencesManager: PreferencesManager,
    private val healthServicesManager: HealthServicesManager
) : ViewModel() {

    val isTracking: StateFlow<Boolean> = sleepRepository.isTracking

    private val _sleepDurationMinutes = MutableStateFlow(480) // 8 hours default
    val sleepDurationMinutes: StateFlow<Int> = _sleepDurationMinutes

    private val _permissionRequestEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 0)
    val permissionRequestEvent = _permissionRequestEvent.asSharedFlow()

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

    private val _isTrackingState = MutableStateFlow(false)
    val isTrackingState: StateFlow<Boolean> = _isTrackingState

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName

    init {
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
            preferencesManager.userName.collectLatest {
                _userName.value = it
            }
        }
        // Link Repository Heart Rate to ViewModel State
        // This ensures the tracking screen shows live data from HealthServices during sleep monitoring
        viewModelScope.launch {
            sleepRepository.heartRate.collectLatest { bpm ->
                if (bpm > 0) {
                    _currentHeartRate.value = bpm
                }
            }
        }
    }

    fun setDuration(minutes: Int) {
        _sleepDurationMinutes.value = minutes
        viewModelScope.launch {
            preferencesManager.saveSleepDuration(minutes)
        }
    }

    fun startTracking() {
        if (isTrackingState.value) return // منع التكرار
        _isTrackingState.value = true
        
        viewModelScope.launch {
            _permissionRequestEvent.emit(Unit)
        }
        
        android.widget.Toast.makeText(context, "Starting Service...", android.widget.Toast.LENGTH_SHORT).show()
        val intent = Intent(context, SleepMonitorService::class.java).apply {
            putExtra(SleepMonitorService.EXTRA_SLEEP_DURATION, _sleepDurationMinutes.value * 60 * 1000L)
        }
        try {
            context.startForegroundService(intent)
            sleepRepository.setTracking(true)
            sleepRepository.startMonitoring() // تشغيل المراقبة الخلفية فقط عند الضغط على الزر
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error starting service: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
            _isTrackingState.value = false // تصفير الحالة في حال الفشل
        }
    }

    fun checkPermissions() {
        val sensorsGranted = context.checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val backgroundSensorsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission("android.permission.BODY_SENSORS_BACKGROUND") == PackageManager.PERMISSION_GRANTED
        } else true
        
        // لا نقوم بتشغيل المراقبة هنا أبداً، ننتظر ضغطة المستخدم على الزر
    }

    fun stopTracking() {
        _isTrackingState.value = false
        val intent = Intent(context, SleepMonitorService::class.java).apply {
            action = SleepMonitorService.ACTION_STOP_MONITORING
        }
        context.startForegroundService(intent)
        sleepRepository.setTracking(false)
        healthServicesManager.updateSleepState(SleepState.UNKNOWN)
        
        // إيقاف الاهتزاز عند الضغط على STOP
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.cancel()
    }

    fun simulateSleep() {
        android.widget.Toast.makeText(context, "Simulating Sleep...", android.widget.Toast.LENGTH_SHORT).show()
        // إرسال إشارة مباشرة للخدمة لضمان الاستجابة الفورية مع تزويدها بالوقت المختار
        val intent = Intent(context, com.smartsleep.alarm.service.SleepMonitorService::class.java).apply {
            action = com.smartsleep.alarm.service.SleepMonitorService.ACTION_SIMULATE_SLEEP
            putExtra(com.smartsleep.alarm.service.SleepMonitorService.EXTRA_SLEEP_DURATION, _sleepDurationMinutes.value * 60 * 1000L)
        }
        context.startForegroundService(intent)
    }

    fun resetSleepSummary() {
        viewModelScope.launch {
            sleepRepository.saveSleepStartTime(0L)
        }
    }

    fun updateMotion(magnitude: Float) {
        // فلترة: لا تحدث الواجهة إلا إذا كان التغيير ملموساً (أكبر من 0.05)
        if (Math.abs(_currentMotion.value - magnitude) > 0.05f) {
            _currentMotion.value = magnitude
        }
    }

    fun updateOnBodyStatus(isOnBody: Boolean) {
        healthServicesManager.updateOnBodyStatus(isOnBody)
    }

    fun updateHeartRate(bpm: Float) {
        // فلترة: لا تحدث الواجهة لنبض القلب إلا إذا تغيرت القيمة الصحيحة
        if (_currentHeartRate.value.toInt() != bpm.toInt()) {
            _currentHeartRate.value = bpm
        }
    }

    fun saveName(name: String) {
        viewModelScope.launch {
            preferencesManager.saveUserName(name)
        }
    }
}
