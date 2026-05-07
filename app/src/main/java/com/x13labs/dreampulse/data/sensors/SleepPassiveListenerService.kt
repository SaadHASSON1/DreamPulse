package com.x13labs.dreampulse.data.sensors

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.PassiveMonitoringUpdate
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import androidx.health.services.client.data.DataType
import com.x13labs.dreampulse.domain.model.SleepState
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

    override fun onNewDataPointsReceived(dataPoints: androidx.health.services.client.data.DataPointContainer) {
        val samples = dataPoints.getData(DataType.HEART_RATE_BPM)
        if (samples.isNotEmpty()) {
            val lastBpm = samples.last().value
            healthServicesManager.updateHeartRate(lastBpm.toFloat())
        }
    }

    override fun onPermissionLost() {
        android.util.Log.e("PassiveListener", "Permission lost for BODY_SENSORS_BACKGROUND")
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = androidx.core.app.NotificationCompat.Builder(this, com.x13labs.dreampulse.util.NotificationHelper.TRACKING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("DreamPulse needs permission")
            .setContentText("Background sensor permission lost. Please re-grant it to track sleep.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(888, notification)
    }


}
