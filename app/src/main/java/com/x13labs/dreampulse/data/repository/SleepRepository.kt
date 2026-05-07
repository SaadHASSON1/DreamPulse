package com.x13labs.dreampulse.data.repository

import com.x13labs.dreampulse.data.sensors.HealthServicesManager
import com.x13labs.dreampulse.domain.model.SleepState
import com.x13labs.dreampulse.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val healthServicesManager: HealthServicesManager,
    private val preferencesManager: PreferencesManager
) {
    val sleepState: StateFlow<SleepState> = healthServicesManager.sleepState
    val isTracking: StateFlow<Boolean> = healthServicesManager.isTracking
    val isTrackingActive: Flow<Boolean> = preferencesManager.isTrackingActive
    val heartRate: StateFlow<Float> = healthServicesManager.heartRate

    fun setTracking(tracking: Boolean) {
        healthServicesManager.setTracking(tracking)
        // حفظ الحالة للتعافي بعد الـ Boot
        GlobalScope.launch {
            preferencesManager.setTrackingActive(tracking)
        }
    }

    suspend fun saveSleepStartTime(timestamp: Long) {
        preferencesManager.saveSleepStartTime(timestamp)
    }

    val sleepStartTime: Flow<Long> = preferencesManager.sleepStartTime
    val userName: Flow<String> = preferencesManager.userName

    suspend fun saveUserName(name: String) {
        preferencesManager.saveUserName(name)
    }

    val lastSleepSummary: Flow<String> = preferencesManager.lastSleepSummary

    suspend fun saveSleepSummary(summary: String) {
        preferencesManager.saveSleepSummary(summary)
    }

    suspend fun isTrackingSupported(): Boolean {
        return healthServicesManager.isTrackingSupported()
    }

    fun startMonitoring() {
        healthServicesManager.startPassiveSleepMonitoring()
    }

    fun stopMonitoring() {
        healthServicesManager.stopPassiveSleepMonitoring()
    }
}
