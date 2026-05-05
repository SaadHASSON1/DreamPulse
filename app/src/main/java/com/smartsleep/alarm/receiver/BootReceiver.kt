package com.smartsleep.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    
    @javax.inject.Inject lateinit var preferencesManager: com.smartsleep.alarm.data.local.PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "onReceive: ${intent.action}")
        
        // Bug #4 fix: Use goAsync() to keep the process alive until the coroutine finishes
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isActive = preferencesManager.isTrackingActive.first()
                if (isActive) {
                    Log.d("BootReceiver", "Tracking was active — restarting SleepMonitorService")
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
                        Log.e("BootReceiver", "Failed to restart service", e)
                    }
                } else {
                    Log.d("BootReceiver", "Tracking was NOT active — doing nothing")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error in boot recovery", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
