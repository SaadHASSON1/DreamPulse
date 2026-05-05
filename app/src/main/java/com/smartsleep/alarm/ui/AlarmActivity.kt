package com.smartsleep.alarm.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
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
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@AndroidEntryPoint
class AlarmActivity : ComponentActivity(), SensorEventListener {
    
    @javax.inject.Inject lateinit var sleepRepository: com.smartsleep.alarm.data.repository.SleepRepository
    @javax.inject.Inject lateinit var preferencesManager: com.smartsleep.alarm.data.local.PreferencesManager

    private var showSummary = mutableStateOf(false)
    private var sleepStartTimeState = mutableStateOf(0L)
    private var sensorManager: SensorManager? = null
    private var lastShakeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        super.onCreate(savedInstanceState)
        
        if (intent?.getStringExtra("action") == "show_summary") {
            showSummary.value = true
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        
        lifecycleScope.launch {
            sleepRepository.sleepStartTime.collect { timestamp ->
                sleepStartTimeState.value = timestamp
            }
        }

        setContent {
            androidx.activity.compose.BackHandler { }
            val motivationPhrase = remember { ThemeUtils.getRandomPhrase("") }
            
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (showSummary.value) {
                        StarryBackground()
                    }
                    
                    if (!showSummary.value) {
                        AlarmScreen(
                            onDismiss = { dismissAlarm() }
                        )
                    } else {
                        SummaryScreen(
                            motivationPhrase = motivationPhrase,
                            startTime = sleepStartTimeState.value,
                            onFinish = { stopAlarmAndFinish() }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AlarmScreen(onDismiss: () -> Unit) {
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
                text = "Good morning",
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
                        onLongClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("HOLD TO DISMISS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "or shake to dismiss",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.2f))
        }
    }

    @Composable
    private fun SummaryScreen(motivationPhrase: String, startTime: Long, onFinish: () -> Unit) {
        val now = System.currentTimeMillis()
        val durationMs = if (startTime > 0 && startTime < now) now - startTime else 0L
        val hours = durationMs / (3600 * 1000)
        val minutes = (durationMs % (3600 * 1000)) / 60000

        // Lift rocket starting point even HIGHER (-140f instead of -110f)
        val rocketY = remember { Animatable(-140f) }
        val startPos = -140f

        LaunchedEffect(Unit) {
            rocketY.snapTo(startPos)
            while (true) {
                rocketY.animateTo(
                    targetValue = -450f,
                    animationSpec = tween(2000, easing = FastOutLinearInEasing)
                )
                rocketY.snapTo(450f)
                rocketY.animateTo(
                    targetValue = startPos,
                    animationSpec = tween(1500, easing = LinearOutSlowInEasing)
                )
                delay(2500)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_neon_rocket),
                contentDescription = "Rocket",
                modifier = Modifier
                    .size(65.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationY = rocketY.value
                    }
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                
                Text(
                    text = motivationPhrase.uppercase(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF90CAF9),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "SLEPT: ${hours}h ${minutes}m",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(0.08f))

                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(0.85f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5)),
                    shape = RoundedCornerShape(21.dp)
                ) {
                    Text("START MY DAY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "or shake",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(0.28f))
            }
        }
    }

    private fun dismissAlarm() {
        Log.d("AlarmActivity", "dismissAlarm() called")
        stopAlarmOnly()
        val monitorServiceIntent = Intent(this, com.smartsleep.alarm.service.SleepMonitorService::class.java)
        stopService(monitorServiceIntent)
        sleepRepository.setTracking(false)
        showSummary.value = true
        
        // Bug #3 fix: Clear ALL persisted state to prevent phantom alarms after reboot
        lifecycleScope.launch {
            preferencesManager.saveSleepConfirmed(false)
            preferencesManager.saveServiceStartTime(0L)
            preferencesManager.saveTargetWakeTime(0L)
            preferencesManager.setTrackingActive(false)
        }
        
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val acceleration = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]) - 9.81f
            if (acceleration > 15f) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 2000) { // 2 seconds cooldown
                    lastShakeTime = now
                    if (!showSummary.value) {
                        dismissAlarm()
                    } else {
                        stopAlarmAndFinish()
                    }
                }
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
        
        // Use GlobalScope so writes survive activity destruction
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sleepRepository.saveSleepStartTime(0L)
            preferencesManager.saveSleepConfirmed(false)
            preferencesManager.saveServiceStartTime(0L)
            preferencesManager.saveTargetWakeTime(0L)
            preferencesManager.setTrackingActive(false)
        }
        
        // Small delay to let DataStore writes start before killing the process
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
            finishAndRemoveTask()
        }, 200)
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }
}

@Composable
fun StarryBackground() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val random = java.util.Random(42)
        repeat(100) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val alpha = random.nextFloat() * 0.4f + 0.1f
            val radius = random.nextFloat() * 1.2f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
