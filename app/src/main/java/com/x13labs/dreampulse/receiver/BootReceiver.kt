package com.x13labs.dreampulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.x13labs.dreampulse.data.local.BootAlarmStore
import com.x13labs.dreampulse.service.SleepMonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Brings an active session back after a restart or an app update, and re-plans the wake-by
 * time when the time zone changes.
 *
 * Direct boot aware: on a watch with a screen lock, LOCKED_BOOT_COMPLETED arrives before the
 * user unlocks, while DataStore still cannot be read. The alarm is re-armed right away from
 * [BootAlarmStore]; the full session (sensors, smart wake) resumes on BOOT_COMPLETED.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @javax.inject.Inject lateinit var preferencesManager: com.x13labs.dreampulse.data.local.PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "onReceive: ${intent.action}")

        if (!BootAlarmStore.isUnlocked(context)) {
            rearmBeforeUnlock(context)
            return
        }

        // goAsync() keeps the process alive until the coroutine finishes
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (preferencesManager.isTrackingActive.first()) {
                    Log.d("BootReceiver", "Tracking was active, bringing SleepMonitorService back")
                    val serviceIntent = Intent(context, SleepMonitorService::class.java)
                    if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                        serviceIntent.action = SleepMonitorService.ACTION_TIME_ZONE_CHANGED
                    }
                    try {
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Failed to restart service", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error in boot recovery", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun rearmBeforeUnlock(context: Context) {
        val target = BootAlarmStore.target(context)
        val now = System.currentTimeMillis()
        if (target <= 0 || now - target > STALE_MS) return
        // Rings a minute from now if the wake time passed while the watch was off
        val time = target.coerceAtLeast(now + 60_000L)
        Log.d("BootReceiver", "Locked boot: alarm re-armed")
        try {
            AlarmReceiver.schedule(context, time)
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to re-arm alarm before unlock", e)
        }
    }

    companion object {
        /** Same as the service: a wake time that passed longer ago than this is over. */
        private const val STALE_MS = 2 * 60 * 60 * 1000L
    }
}
