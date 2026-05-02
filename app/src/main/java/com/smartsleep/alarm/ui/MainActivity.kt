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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import com.smartsleep.alarm.ui.screens.MainScreen
import com.smartsleep.alarm.ui.theme.SmartSleepTheme
import com.smartsleep.alarm.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: MainViewModel by viewModels()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var offBodySensor: Sensor? = null
    private var heartRateSensor: Sensor? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permissions result: $permissions")
        if (!permissions.all { it.value }) {
            Log.w("MainActivity", "Some permissions were denied or background sensor needs manual approval")
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

        // مراقبة طلب الصلاحيات اليدوي من الـ ViewModel
        lifecycleScope.launch {
            viewModel.permissionRequestEvent.collect {
                checkPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // مراقبة حالة التتبع لتشغيل الحساسات في الواجهة "فقط" عند الحاجة
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
        
        // Allow Off-Body detection even during tracking to keep the "Active" status live
        if (sensorType == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val isOnBody = event.values[0] == 1.0f
            viewModel.updateOnBodyStatus(isOnBody)
            return
        }

        // For Heart Rate and Motion, we let the Service handle them during tracking to avoid hardware conflicts
        if (viewModel.isTracking.value) return
        
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
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
                val bpm = event.values[0]
                if (bpm > 0) {
                    viewModel.updateHeartRate(bpm)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkPermissions() {
        Log.d("MainActivity", "Checking permissions...")
        
        val hasBody = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasBg = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS_BACKGROUND) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        Toast.makeText(this, "Sensors: $hasBody, Background: $hasBg", Toast.LENGTH_SHORT).show()

        val primaryPermissions = mutableListOf(
            android.Manifest.permission.BODY_SENSORS,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            primaryPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            primaryPermissions.add(android.Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }

        val missingPrimary = primaryPermissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPrimary.isNotEmpty()) {
            Log.d("MainActivity", "Requesting primary permissions: $missingPrimary")
            requestPermissionLauncher.launch(missingPrimary.toTypedArray())
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS_BACKGROUND) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting Background Sensors permission")
                android.widget.Toast.makeText(this, "IMPORTANT: Please set Sensor Permission to 'Allow all the time'", android.widget.Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.BODY_SENSORS_BACKGROUND))
            }
        }

        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivity", "Battery optimization NOT ignored. Requesting...")
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Battery optimization request failed", e)
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e("MainActivity", "All battery settings requests failed", e2)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }
}
