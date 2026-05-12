package com.x13labs.dreampulse.ui

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
import com.x13labs.dreampulse.ui.screens.MainScreen
import com.x13labs.dreampulse.ui.theme.SmartSleepTheme
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sqrt

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: MainViewModel by viewModels()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var offBodySensor: Sensor? = null
    private var heartRateSensor: Sensor? = null

    private var pendingStartTracking = false

    // Launcher للأذونات عند بداية التطبيق (بدون تشغيل tracking)
    private val startupPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Startup permissions result: $permissions")
        // طلب BODY_SENSORS_BACKGROUND بعد منح الأذونات الأساسية
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND))
            }
        }
    }

    // Launcher للأذونات عند الضغط على Start Tracking
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permissions result: $permissions")
        if (pendingStartTracking) {
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

        // ← طلب الأذونات فور فتح التطبيق
        requestStartupPermissions()

        lifecycleScope.launch {
            viewModel.needsPermissionCheck.collect {
                Log.d("MainActivity", "Received permission check request from ViewModel")
                pendingStartTracking = true
                handleStartTrackingRequest()
            }
        }
    }

    /** يطلب كل الأذونات Runtime فور فتح التطبيق، بدون تشغيل التتبع */
    private fun requestStartupPermissions() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.BODY_SENSORS)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            Log.d("MainActivity", "Requesting startup permissions: $needed")
            startupPermissionLauncher.launch(needed.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // الأذونات الأساسية موجودة — اطلب الخلفية مباشرة
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND))
            }
        }
    }

    private fun handleStartTrackingRequest() {
        Log.d("MainActivity", "handleStartTrackingRequest() — checking permissions...")

        // فحص الأذونات الأساسية (يجب أن تكون ممنوحة من بداية التطبيق)
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.BODY_SENSORS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w("MainActivity", "Still missing at launch: $missingPermissions — re-requesting")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        // كل الأذونات موجودة → شغّل التتبع
        Log.d("MainActivity", "All permissions OK — calling executeStartTracking()")
        pendingStartTracking = false
        viewModel.executeStartTracking()
    }

    override fun onResume() {
        super.onResume()

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