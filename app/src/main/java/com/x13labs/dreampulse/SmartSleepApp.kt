package com.x13labs.dreampulse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartSleepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.x13labs.dreampulse.util.CrashLog.install(this)
    }
}
