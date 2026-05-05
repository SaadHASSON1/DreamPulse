package com.smartsleep.alarm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.smartsleep.alarm.ui.screens.MainScreen
import com.smartsleep.alarm.ui.theme.SmartSleepTheme
import com.smartsleep.alarm.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sqrt

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: MainViewModel by viewModels()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var offBodySensor: Sensor? = null
    private var heartRateSensor: Sensor? = null

    // Track whether the user intended to start tracking (to auto-continue after permission grant)
    private var pendingStartTracking = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permissions result: $permissions")
        if (!permissions.all { it.value }) {
            Log.w("MainActivity", "Some permissions were denied")
        }
        // If we have a pending start-tracking request, continue the chain
        if (pendingStartTracking) {
            Log.d("MainActivity", "Continuing permission chain after grant...")
            handleStartTrackingRequest()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("MainActivity", "onCreate called (Clean Start)")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        
        setContent {
            SmartSleepTheme {
                MainScreen(viewModel)
            }
        }

        // Listen for permission check requests from the ViewModel
        lifecycleScope.launch {
            viewModel.needsPermissionCheck.collect {
                Log.d("MainActivity", "Received permission check request from ViewModel")
                pendingStartTracking = true
                handleStartTrackingRequest()
            }
        }
    }

    /**
     * Central method that checks all permissions in sequence.
     * If everything is granted, it calls executeStartTracking().
     * If something is missing, it requests it and returns (the launcher callback will re-call this).
     */
    private fun handleStartTrackingRequest() {
        Log.d("MainActivity", "handleStartTrackingRequest() — checking permissions...")
        
        // Step 1: Runtime permissions (BODY_SENSORS, ACTIVITY_RECOGNITION, POST_NOTIFICATIONS)
        val missingPermissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BODY_SENSORS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_HEALTH) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d("MainActivity", "Missing primary permissions: $missingPermissions")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            return  // Launcher callback will call handleStartTrackingRequest() again
        }

        // Step 2: Background body sensors (must be requested AFTER foreground sensor is granted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting BODY_SENSORS_BACKGROUND")
                Toast.makeText(this, "Please set Sensor Permission to 'Allow all the time'", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND))
                return
            }
        }

        // Step 3: Battery optimization exemption
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivity", "Requesting battery optimization exemption")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                // Don't return here — we'll let the user continue even if they dismiss
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Battery optimization request failed", e)
        }

        // Step 4: Exact alarms (best-effort, don't block)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.d("MainActivity", "Requesting exact alarm permission")
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exact alarm settings not available on this device", e)
            }
        }

        // Step 5: Full-screen intent (best-effort, don't block)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                if (nm?.canUseFullScreenIntent() == false) {
                    Log.d("MainActivity", "Requesting full-screen intent permission")
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Full screen intent settings not available", e)
            }
        }

        // Step 6: Samsung battery briefing (non-blocking)
        showSamsungBatteryBriefingIfNeeded()

        // ALL CHECKS PASSED — actually start tracking!
        Log.d("MainActivity", "All permissions OK — calling executeStartTracking()")
        pendingStartTracking = false
        viewModel.executeStartTracking()
    }

    override fun onResume() {
        super.onResume()
        
        // Register UI sensors when tracking is active
        lifecycleScope.launch {
            viewModel.isTrackingState.collectLatest { active ->
                if (active) {
                    accelerometer?.let { sensorManager?.registerListener(this@MainActivity, it, SensorManager.SENSOR_DELAY_UI) }
                    heartRateSensor?.let { sensorManager?.registerListener(this@MainActivity, it, SensorManager.SENSOR_DELAY_UI) }
                    offBodySensor?.let { sensorManager?.registerListener(this@MainActivity, it, SensorManager.SENSOR_DELAY_NORMAL) }
                } else {
                    sensorManager?.unregisterListener(this@MainActivity)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorType = event?.sensor?.type
        
        if (sensorType == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val isOnBody = event.values[0] == 1.0f
            viewModel.updateOnBodyStatus(isOnBody)
            return
        }

        if (viewModel.isTrackingState.value) return
        
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event!!.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val magnitude = sqrt(x * x + y * y + z * z) - 9.8f
                val absMagnitude = if (magnitude < 0) -magnitude else magnitude
                
                if (absMagnitude > 0.05f) {
                    viewModel.updateMotion(absMagnitude)
                } else {
                    viewModel.updateMotion(0f)
                }
            }
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event!!.values[0]
                if (bpm > 0) {
                    viewModel.updateHeartRate(bpm)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showSamsungBatteryBriefingIfNeeded() {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return
        
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("samsung_battery_briefing_shown", false)) return

        try {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Samsung Watch Setup")
            builder.setMessage("To ensure the alarm fires, please add DreamPulse to 'Never sleeping apps'.\n\nSettings → Battery and device care → Battery → Background usage limits → Never sleeping apps")
            builder.setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to open Samsung settings", e)
                }
                prefs.edit().putBoolean("samsung_battery_briefing_shown", true).apply()
            }
            builder.setCancelable(true)
            builder.show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to show Samsung dialog", e)
        }
    }
}
