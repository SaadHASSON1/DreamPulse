package com.x13labs.dreampulse.data.repository

import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.data.sensors.HealthServicesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Tracking flag (in memory for the UI, persisted for recovery) and the sleep start time. */
@Singleton
class SleepRepository @Inject constructor(
    private val healthServicesManager: HealthServicesManager,
    private val preferencesManager: PreferencesManager,
) {
    // Outlives any screen or service, so the write is not cancelled with its caller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isTracking: StateFlow<Boolean> = healthServicesManager.isTracking

    fun setTracking(tracking: Boolean) {
        healthServicesManager.setTracking(tracking)
        // Fails while the watch is locked after a restart (the alarm can ring then): not fatal
        scope.launch { runCatching { preferencesManager.setTrackingActive(tracking) } }
    }

    suspend fun saveSleepStartTime(timestamp: Long) {
        preferencesManager.saveSleepStartTime(timestamp)
    }

    fun stopMonitoring() {
        healthServicesManager.stopPassiveSleepMonitoring()
    }
}
