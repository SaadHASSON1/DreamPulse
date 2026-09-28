package com.x13labs.dreampulse.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.data.local.BootAlarmStore
import com.x13labs.dreampulse.ui.screens.MainScreen
import com.x13labs.dreampulse.ui.screens.OnboardingScreen
import com.x13labs.dreampulse.ui.screens.OnboardingStep
import com.x13labs.dreampulse.ui.theme.DreamTheme
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingStartTracking = false
    /** Permission rounds for the current press of Start (foreground first, then background). */
    private var startRequestRounds = 0
    private var showOnboarding by mutableStateOf(false)
    private var afterOnboardingPermission: (() -> Unit)? = null

    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    /** Permission asked by one onboarding step: move on whatever the answer. */
    private val onboardingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Onboarding permissions result: $permissions")
        afterOnboardingPermission?.invoke()
        afterOnboardingPermission = null
    }

    /** Permissions checked again when Start is pressed. */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permissions result: $permissions")
        if (pendingStartTracking) handleStartTrackingRequest()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Never in the way of a running night (e.g. right after updating from an older version)
        showOnboarding = !prefs.getBoolean(KEY_ONBOARDING_DONE, false) && BootAlarmStore.target(this) == 0L
        val steps = onboardingSteps()

        setContent {
            DreamTheme {
                val tracking by viewModel.isTrackingState.collectAsState()
                if (showOnboarding && !tracking) {
                    OnboardingScreen(steps, onAction = ::runOnboardingStep, onFinish = ::finishOnboarding)
                } else {
                    MainScreen(viewModel)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.needsPermissionCheck.collect {
                Log.d("MainActivity", "Received permission check request from ViewModel")
                pendingStartTracking = true
                startRequestRounds = 0
                handleStartTrackingRequest()
            }
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Only the steps that still need something from the user. */
    private fun onboardingSteps(): List<OnboardingStep> = buildList {
        add(OnboardingStep.WELCOME)
        if (!granted(Manifest.permission.BODY_SENSORS) || !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            add(OnboardingStep.SENSORS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!granted(Manifest.permission.BODY_SENSORS_BACKGROUND)) add(OnboardingStep.BACKGROUND)
            if (!granted(Manifest.permission.POST_NOTIFICATIONS)) add(OnboardingStep.NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            add(OnboardingStep.ALARM_SCREEN)
        }
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) add(OnboardingStep.BATTERY)
        add(OnboardingStep.READY)
    }

    private fun runOnboardingStep(step: OnboardingStep, done: () -> Unit) {
        fun ask(vararg permissions: String) {
            afterOnboardingPermission = done
            onboardingPermissionLauncher.launch(arrayOf(*permissions))
        }
        when (step) {
            OnboardingStep.SENSORS -> ask(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION)
            OnboardingStep.BACKGROUND -> ask(Manifest.permission.BODY_SENSORS_BACKGROUND)
            OnboardingStep.NOTIFICATIONS -> ask(Manifest.permission.POST_NOTIFICATIONS)
            OnboardingStep.ALARM_SCREEN -> {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                done()
            }
            OnboardingStep.BATTERY -> {
                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                done()
            }
            OnboardingStep.WELCOME, OnboardingStep.READY -> done()
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open $action", e)
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
                Log.e("MainActivity", "Failed to open app settings", e2)
            }
        }
    }

    private fun finishOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        showOnboarding = false
    }

    private fun handleStartTrackingRequest() {
        Log.d("MainActivity", "handleStartTrackingRequest() — checking permissions...")

        val missing = mutableListOf<String>()
        if (!granted(Manifest.permission.BODY_SENSORS)) missing.add(Manifest.permission.BODY_SENSORS)
        if (!granted(Manifest.permission.ACTIVITY_RECOGNITION)) missing.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.BODY_SENSORS_BACKGROUND)) {
            missing.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }

        if (missing.isNotEmpty()) {
            if (startRequestRounds >= MAX_REQUEST_ROUNDS) {
                // Asked already and the user (or the system) refused: stop here instead of
                // re-requesting in a loop, and send the user to the app's settings page.
                Log.w("MainActivity", "Permissions still denied: $missing")
                pendingStartTracking = false
                Toast.makeText(this, getString(R.string.perm_denied), Toast.LENGTH_LONG).show()
                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                return
            }
            startRequestRounds++
            // The background permission can only be granted once the foreground one is
            val ask = missing.filter { it != Manifest.permission.BODY_SENSORS_BACKGROUND }.ifEmpty { missing }
            Log.w("MainActivity", "Missing at start: $missing — requesting $ask")
            requestPermissionLauncher.launch(ask.toTypedArray())
            return
        }

        Log.d("MainActivity", "All permissions OK — calling executeStartTracking()")
        pendingStartTracking = false
        viewModel.executeStartTracking()
    }

    companion object {
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val MAX_REQUEST_ROUNDS = 2
    }
}
