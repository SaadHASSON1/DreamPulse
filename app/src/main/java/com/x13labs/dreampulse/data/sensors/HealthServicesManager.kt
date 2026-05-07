/* 
 * Copyright (C) 2026 Saad - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.x13labs.dreampulse.data.sensors

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityState
import androidx.health.services.client.data.DataType
import com.x13labs.dreampulse.domain.model.SleepState
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

    private val measureClient by lazy { healthServicesClient?.measureClient }

    fun startPassiveSleepMonitoring() {
        val config = PassiveListenerConfig.builder()
            .setShouldUserActivityInfoBeRequested(true)
            .setDataTypes(setOf(androidx.health.services.client.data.DataType.HEART_RATE_BPM))
            .build()
        
        passiveMonitoringClient?.setPassiveListenerServiceAsync(
            SleepPassiveListenerService::class.java,
            config
        )
        
        // البدء بمراقبة النبض اللحظي لضمان استيقاظ الحساس
        startHeartRateMeasurement()
    }

    fun resetStates() {
        _heartRate.value = 0f
        _sleepState.value = SleepState.UNKNOWN
        _isSimulation.value = false
        _isOnBody.value = false
    }


    private var hrCallback: androidx.health.services.client.MeasureCallback? = null

    fun startHeartRateMeasurement(forceRestart: Boolean = false) {
        
        try {
            if (!forceRestart && hrCallback != null) return // إذا كان يعمل بالفعل، اتركه وشأنه
            
            // تنظيف شامل فقط إذا طلبنا إعادة التشغيل القسرية
            hrCallback?.let { measureClient?.unregisterMeasureCallbackAsync(androidx.health.services.client.data.DataType.HEART_RATE_BPM, it) }
            hrCallback = null
            
            val dataType = androidx.health.services.client.data.DataType.HEART_RATE_BPM
            val callback = object : androidx.health.services.client.MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: androidx.health.services.client.data.DeltaDataType<*, *>,
                    availability: androidx.health.services.client.data.Availability
                ) {
                    Log.d("HealthServices", "HR Availability: $availability")
                }

                override fun onDataReceived(data: androidx.health.services.client.data.DataPointContainer) {
                    val samples = data.getData(dataType)
                    if (samples.isNotEmpty()) {
                        val lastBpm = samples.last().value
                        updateHeartRate(lastBpm.toFloat())
                        
                        if (System.currentTimeMillis() % 60000 < 2000) {
                            Log.d("HealthServices", "Pulse received: $lastBpm")
                        }
                    }
                }
            }
            
            hrCallback = callback
            measureClient?.registerMeasureCallback(dataType, callback)
            Log.i("HealthServices", "HR Measurement started/reset successfully")
        } catch (e: Exception) {
            Log.e("HealthServices", "Failed to start HR", e)
        }
    }

    fun stopHeartRateMeasurement() {
        hrCallback?.let {
            measureClient?.unregisterMeasureCallbackAsync(
                androidx.health.services.client.data.DataType.HEART_RATE_BPM,
                it
            )
            hrCallback = null
        }
    }

    fun stopPassiveSleepMonitoring() {
        passiveMonitoringClient?.clearPassiveListenerServiceAsync()
        stopHeartRateMeasurement()
        // NOTE: Do NOT set _isTracking = false here!
        // Tracking state is managed exclusively by setTracking() calls.
        // This method is called during confirmSleep() where tracking must remain true.
    }

    fun unregisterAll() {
        try {
            passiveMonitoringClient?.clearPassiveListenerServiceAsync()
        } catch (e: Exception) {}
        try {
            stopHeartRateMeasurement()
        } catch (e: Exception) {}
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
        val capabilities = passiveMonitoringClient?.getCapabilitiesAsync()?.await()
        return capabilities?.supportedDataTypesPassiveMonitoring?.contains(androidx.health.services.client.data.DataType.HEART_RATE_BPM) ?: false
    }
}
