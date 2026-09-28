package com.x13labs.dreampulse.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.launch
import com.x13labs.dreampulse.ui.screens.MainScreen
import com.x13labs.dreampulse.ui.theme.DreamTheme
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingStartTracking = false

    // Launcher للأذونات عند بداية التطبيق (بدون تشغيل tracking)
    private val startupPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Startup permissions result: $permissions")
        // طلب BODY_SENSORS_BACKGROUND بعد منح الأذونات الأساسية
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            handleStartTrackingRequest(afterRequest = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate called (Clean Start)")

        setContent {
            DreamTheme {
                MainScreen(viewModel)
            }
        }

        // ← طلب الأذونات فور فتح التطبيق
        requestStartupPermissions()
        showSamsungBatteryBriefingIfNeeded()

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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // الأذونات الأساسية موجودة — اطلب الخلفية مباشرة
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND))
            }
        }
    }

    private fun handleStartTrackingRequest(afterRequest: Boolean = false) {
        Log.d("MainActivity", "handleStartTrackingRequest() — checking permissions...")

        // فحص الأذونات الأساسية (يجب أن تكون ممنوحة من بداية التطبيق)
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.BODY_SENSORS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }

        if (missingPermissions.isNotEmpty()) {
            if (afterRequest) {
                // Already asked once and the user (or the system) refused: stop here instead of
                // re-requesting in a loop, and send the user to the app's settings page.
                Log.w("MainActivity", "Permissions still denied after request: $missingPermissions")
                pendingStartTracking = false
                Toast.makeText(this, getString(com.x13labs.dreampulse.R.string.perm_denied), Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to open app settings", e)
                }
                return
            }
            Log.w("MainActivity", "Missing at launch: $missingPermissions — requesting")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        // كل الأذونات موجودة → شغّل التتبع
        Log.d("MainActivity", "All permissions OK — calling executeStartTracking()")
        pendingStartTracking = false
        viewModel.executeStartTracking()
    }

    private fun showSamsungBatteryBriefingIfNeeded() {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("samsung_battery_briefing_shown", false)) return

        try {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle(getString(com.x13labs.dreampulse.R.string.samsung_title))
            builder.setMessage(getString(com.x13labs.dreampulse.R.string.samsung_body))
            builder.setPositiveButton(getString(com.x13labs.dreampulse.R.string.perm_open)) { _, _ ->
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