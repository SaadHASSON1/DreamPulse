package com.smartsleep.alarm.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.smartsleep.alarm.util.ThemeUtils
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

@AndroidEntryPoint
class AlarmActivity : ComponentActivity(), SensorEventListener {
    
    @javax.inject.Inject lateinit var sleepRepository: com.smartsleep.alarm.data.repository.SleepRepository

    private var showSummary = mutableStateOf(false)
    private var sleepStartTimeState = mutableStateOf(0L)
    private var userNameState = mutableStateOf("")
    private var sensorManager: SensorManager? = null

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle intent action for summary
        if (intent?.getStringExtra("action") == "show_summary") {
            showSummary.value = true
        }

        // Screen On Logic
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        
        lifecycleScope.launch {
            sleepRepository.sleepStartTime.collect { timestamp ->
                sleepStartTimeState.value = timestamp
            }
        }
        
        lifecycleScope.launch {
            sleepRepository.userName.collect { name ->
                userNameState.value = name
            }
        }

        setContent {
            androidx.activity.compose.BackHandler { }
            val userName = userNameState.value
            val motivationPhrase = remember(userName) { ThemeUtils.getRandomPhrase(userName) }
            
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (!showSummary.value) {
                        // --- ALARM SCREEN ---
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(0.15f))

                            val sunTransition = rememberInfiniteTransition()
                            val sunPulse by sunTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                                Image(
                                    painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_normal_sun),
                                    contentDescription = "Sun",
                                    modifier = Modifier
                                        .size(70.dp)
                                        .graphicsLayer {
                                            scaleX = sunPulse
                                            scaleY = sunPulse
                                        }
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.weight(0.1f))

                            Text(
                                text = "WAKE UP",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF8A80),
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                text = "Good morning, $userName",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.weight(0.1f))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFFB71C1C))
                                    .combinedClickable(
                                        onClick = { },
                                        onLongClick = { dismissAlarm() }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("HOLD TO DISMISS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.weight(0.2f))
                        }
                    } else {
                        // --- SUMMARY SCREEN ---
                        val now = System.currentTimeMillis()
                        val startTime = sleepStartTimeState.value
                        val durationMs = if (startTime > 0 && startTime < now) now - startTime else 0L
                        val hours = durationMs / (3600 * 1000)
                        val minutes = (durationMs % (3600 * 1000)) / 60000

                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(0.15f))
                            
                            Text(
                                text = motivationPhrase.uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF90CAF9),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "SLEPT: ${hours}h ${minutes}m",
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.weight(0.15f))

                            Button(
                                onClick = { stopAlarmAndFinish() },
                                modifier = Modifier.fillMaxWidth(0.8f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5))
                            ) {
                                Text("START MY DAY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.weight(0.2f))
                        }
                    }
                }
            }
        }
    }

    private fun dismissAlarm() {
        stopAlarmOnly()
        val monitorServiceIntent = Intent(this, com.smartsleep.alarm.service.SleepMonitorService::class.java)
        stopService(monitorServiceIntent)
        sleepRepository.setTracking(false)
        showSummary.value = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && !showSummary.value) {
            val acceleration = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]) - 9.81f
            if (acceleration > 15f) {
                dismissAlarm()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getStringExtra("action") == "show_summary") {
            showSummary.value = true
        }
    }

    private fun stopAlarmOnly() {
        val alarmServiceIntent = Intent(this, com.smartsleep.alarm.service.AlarmService::class.java)
        stopService(alarmServiceIntent)
    }

    private fun stopAlarmAndFinish() {
        stopAlarmOnly()
        val monitorServiceIntent = Intent(this, com.smartsleep.alarm.service.SleepMonitorService::class.java)
        stopService(monitorServiceIntent)
        sleepRepository.setTracking(false)
        finishAffinity()
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }
}
