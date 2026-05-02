package com.smartsleep.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartsleep.alarm.service.AlarmService
import dagger.hilt.android.AndroidEntryPoint
import android.os.PowerManager

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "🔥 MASTER ALARM TRIGGERED!")
        
        try {
            // 1. Force the CPU to stay awake while we start the service
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // Fix: ACQUIRE_CAUSES_WAKEUP should be used with a screen wake lock if we want to turn on the screen,
            // or just use PARTIAL_WAKE_LOCK without it to keep the CPU alive.
            // Since we want the service to start, a simple PARTIAL_WAKE_LOCK is best here.
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DreamPulse:AlarmReceiverWakeLock"
            )
            wakeLock.acquire(15000) // 15 seconds to ensure service starts

            // 2. Start the AlarmService
            val serviceIntent = Intent(context, AlarmService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "CRITICAL: Failed to start AlarmService", e)
        }
    }
}
