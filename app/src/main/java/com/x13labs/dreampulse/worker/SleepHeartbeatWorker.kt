package com.x13labs.dreampulse.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.service.SleepMonitorService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class SleepHeartbeatWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(context, WorkerEntryPoint::class.java)
        val preferencesManager = entryPoint.preferencesManager()

        val isTracking = preferencesManager.isTrackingActive.first()
        val serviceStartTime = preferencesManager.serviceStartTime.first()
        
        // Restart service if tracking is active, regardless of whether sleep was confirmed.
        // This ensures the service survives Samsung process kills in BOTH phases:
        // 1. Before sleep detection (monitoring phase)
        // 2. After sleep detection (alarm waiting phase)
        if (isTracking && serviceStartTime > 0) {
            Log.d("HeartbeatWorker", "Tracking active — ensuring service is running")
            val serviceIntent = Intent(context, SleepMonitorService::class.java).apply {
                putExtra(SleepMonitorService.EXTRA_SLEEP_DURATION, preferencesManager.sleepDuration.first() * 60 * 1000L)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("HeartbeatWorker", "Failed to restart service", e)
            }
        } else {
            Log.d("HeartbeatWorker", "Tracking not active — nothing to do")
        }
        return Result.success()
    }
}
