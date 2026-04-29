package com.smartsleep.alarm.data.sensors

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityState
import com.smartsleep.alarm.domain.model.SleepState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthServicesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val healthServicesClient by lazy { 
        try { HealthServices.getClient(context) } catch(e: Exception) { null }
    }
    
    private val passiveMonitoringClient by lazy { healthServicesClient?.passiveMonitoringClient }

    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate

    private val _sleepState = MutableStateFlow(SleepState.UNKNOWN)
    val sleepState: StateFlow<SleepState> = _sleepState

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _isOnBody = MutableStateFlow(false)
    val isOnBody: StateFlow<Boolean> = _isOnBody

    private val _isSimulation = MutableStateFlow(false)
    val isSimulation: StateFlow<Boolean> = _isSimulation

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }

    fun setSimulation(active: Boolean) {
        _isSimulation.value = active
    }

    fun startPassiveSleepMonitoring() {
        val config = PassiveListenerConfig.builder()
            .setShouldUserActivityInfoBeRequested(true)
            .build()
        
        passiveMonitoringClient?.setPassiveListenerServiceAsync(
            SleepPassiveListenerService::class.java,
            config
        )
    }

    fun stopPassiveSleepMonitoring() {
        passiveMonitoringClient?.clearPassiveListenerServiceAsync()
    }

    fun updateHeartRate(bpm: Float) {
        _heartRate.value = bpm
    }
    
    fun updateSleepState(state: SleepState) {
        _sleepState.value = state
    }

    fun updateOnBodyStatus(onBody: Boolean) {
        _isOnBody.value = onBody
    }

    suspend fun isTrackingSupported(): Boolean {
        return true
    }
}
