package com.smartsleep.alarm.data.repository

import com.smartsleep.alarm.data.sensors.HealthServicesManager
import com.smartsleep.alarm.domain.model.SleepState
import com.smartsleep.alarm.data.local.PreferencesManager
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
