package com.smartsleep.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // After reboot, we could potentially restart the sleep monitoring service
            // if it was active. For now, we ensure the class exists to prevent build errors.
        }
    }
}
