package com.x13labs.dreampulse.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.service.SleepMonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps SleepMonitorService alive while a session is active.
 *
 * Replaces the old WorkManager heartbeat: since Android 12 a worker is not allowed to start a
 * foreground service, so the restart silently failed on Wear OS 4/5. A broadcast delivered by an
 * exact alarm IS exempt from that restriction (we hold USE_EXACT_ALARM).
 */
@AndroidEntryPoint
class HeartbeatReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isActive = preferencesManager.isTrackingActive.first()
                val startTime = preferencesManager.serviceStartTime.first()
                if (isActive && startTime > 0) {
                    Log.d("Heartbeat", "Session active — ensuring SleepMonitorService is running")
                    try {
                        context.startForegroundService(Intent(context, SleepMonitorService::class.java))
                    } catch (e: Exception) {
                        Log.e("Heartbeat", "Failed to restart service", e)
                    }
                    schedule(context)
                } else {
                    Log.d("Heartbeat", "No active session — heartbeat chain ends")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 1003
        private const val INTERVAL_MS = 15 * 60 * 1000L

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, HeartbeatReceiver::class.java)
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + INTERVAL_MS,
                    pendingIntent(context)
                )
            } catch (e: Exception) {
                Log.e("Heartbeat", "Failed to schedule heartbeat", e)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context))
        }
    }
}
