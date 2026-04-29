package com.smartsleep.alarm.ui.viewmodel

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
import kotlinx.coroutines.flow.collectLatest
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

    val isTracking: StateFlow<Boolean> = sleepRepository.isTracking

    private val _sleepDurationMinutes = MutableStateFlow(480) // 8 hours default
    val sleepDurationMinutes: StateFlow<Int> = _sleepDurationMinutes

    private val _currentSleepState = MutableStateFlow(SleepState.UNKNOWN)
    val currentSleepState: StateFlow<SleepState> = _currentSleepState
    
    private val _currentMotion = MutableStateFlow(0f)
    val currentMotion: StateFlow<Float> = _currentMotion

    private val _sleepStartTime = MutableStateFlow(0L)
    val sleepStartTime: StateFlow<Long> = _sleepStartTime
    
    val isOnBody: StateFlow<Boolean> = healthServicesManager.isOnBody

    private val _currentHeartRate = MutableStateFlow(0f)
    val currentHeartRate: StateFlow<Float> = _currentHeartRate

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
    }

    fun setDuration(minutes: Int) {
        _sleepDurationMinutes.value = minutes
        viewModelScope.launch {
            preferencesManager.saveSleepDuration(minutes)
        }
    }

    fun startTracking() {
        if (isTracking.value) return // منع التكرار
        
        viewModelScope.launch {
            sleepRepository.saveSleepStartTime(System.currentTimeMillis())
        }
        
        android.widget.Toast.makeText(context, "Starting Service...", android.widget.Toast.LENGTH_SHORT).show()
        val intent = Intent(context, SleepMonitorService::class.java).apply {
            putExtra(SleepMonitorService.EXTRA_SLEEP_DURATION, _sleepDurationMinutes.value * 60 * 1000L)
        }
        try {
            context.startForegroundService(intent)
            sleepRepository.setTracking(true)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error starting service: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    fun stopTracking() {
        val intent = Intent(context, SleepMonitorService::class.java)
        context.stopService(intent)
        sleepRepository.setTracking(false)
        healthServicesManager.updateSleepState(SleepState.UNKNOWN)
        
        // إيقاف الاهتزاز عند الضغط على STOP
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.cancel()
    }

    fun simulateSleep() {
        android.widget.Toast.makeText(context, "Simulating Sleep...", android.widget.Toast.LENGTH_SHORT).show()
        // تصفير الحالة أولاً ثم تحويلها لـ ASLEEP لضمان شعور النظام بالتغيير في كل مرة
        viewModelScope.launch {
            healthServicesManager.setSimulation(true)
            healthServicesManager.updateSleepState(com.smartsleep.alarm.domain.model.SleepState.UNKNOWN)
            kotlinx.coroutines.delay(100) // تأخير بسيط جداً لضمان وصول التحديث الأول
            healthServicesManager.updateSleepState(com.smartsleep.alarm.domain.model.SleepState.ASLEEP)
        }
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
