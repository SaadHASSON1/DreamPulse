package com.smartsleep.alarm.data.sensors

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.PassiveMonitoringUpdate
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import androidx.health.services.client.data.DataType
import com.smartsleep.alarm.domain.model.SleepState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SleepPassiveListenerService : PassiveListenerService() {

    @Inject
    lateinit var healthServicesManager: HealthServicesManager

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        if (info.userActivityState == UserActivityState.USER_ACTIVITY_ASLEEP) {
            healthServicesManager.updateSleepState(SleepState.ASLEEP)
        }
    }
}
