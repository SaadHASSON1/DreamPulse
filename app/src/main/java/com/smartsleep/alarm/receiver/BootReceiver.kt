package com.smartsleep.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    
    @javax.inject.Inject lateinit var sleepRepository: com.smartsleep.alarm.data.repository.SleepRepository
    @javax.inject.Inject lateinit var preferencesManager: com.smartsleep.alarm.data.local.PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val isActive = preferencesManager.isTrackingActive.first()
            if (isActive) {
                val duration = preferencesManager.sleepDuration.first()
                val serviceIntent = Intent(context, com.smartsleep.alarm.service.SleepMonitorService::class.java).apply {
                    putExtra(com.smartsleep.alarm.service.SleepMonitorService.EXTRA_SLEEP_DURATION, duration * 60 * 1000L)
                }
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Failed to restart service", e)
                }
            }
        }
    }
}
