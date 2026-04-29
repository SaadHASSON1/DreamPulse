package com.smartsleep.alarm.ui

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Vibrator
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
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
import kotlinx.coroutines.delay
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

@Composable
fun StarryBackground() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val random = java.util.Random(42) // Stable seed
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

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        setContent {
            androidx.activity.compose.BackHandler { }

            val infiniteTransition = rememberInfiniteTransition()
            val userName = userNameState.value
            val motivationPhrase = remember(userName) { ThemeUtils.getRandomPhrase(userName) }
            
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    StarryBackground()
                    
                    if (!showSummary.value) {
                        // --- ALARM SCREEN ---
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(0.15f)) // رفع بسيط للأعلى

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

                            Spacer(modifier = Modifier.weight(0.08f)) // مسافة أكبر قليلاً للراحة البصرية

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "WAKE UP",
                                    fontSize = 24.sp, // تكبير بسيط للعنوان لزيادة الهيبة
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color(0xFFFF8A80),
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Good morning, $userName",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.weight(0.10f))

                            // تجميع الزر والتلميح لزيادة التناسق
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f) // أعرض قليلاً لمظهر أكثر ثباتاً
                                        .height(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFB71C1C).copy(alpha = 0.9f))
                                        .combinedClickable(
                                            onClick = { 
                                                android.widget.Toast.makeText(this@AlarmActivity, "HOLD TO DISMISS", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onLongClick = {
                                                dismissAlarm()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "HOLD TO DISMISS", 
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = "OR SHAKE TO DISMISS",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold, // خط أسمك قليلاً للوضوح
                                    color = Color.White.copy(alpha = 0.3f), // شفافية أكثر ليكون "ثانوياً"
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.weight(0.18f)) // مسافة أمان سفلية لرفع كل شيء
                        }
                    } else {
                        // --- SUMMARY SCREEN ---
                        val now = System.currentTimeMillis()
                        val startTime = sleepStartTimeState.value
                        val durationMs = if (startTime > 0 && startTime < now) now - startTime else 0L
                        val hours = durationMs / (3600 * 1000)
                        val minutes = (durationMs % (3600 * 1000)) / 60000

                        Box(modifier = Modifier.fillMaxSize()) {
                            // 1. Rocket Background Layer
                            val rocketTransition = rememberInfiniteTransition()
                            val rocketOffset by rocketTransition.animateFloat(
                                initialValue = 500f,
                                targetValue = 500f,
                                animationSpec = infiniteRepeatable(
                                    animation = keyframes {
                                        durationMillis = 4500
                                        500f at 0
                                        -130f at 1000 with LinearOutSlowInEasing
                                        -130f at 2800
                                        -450f at 3600 with FastOutLinearInEasing
                                        -450f at 4500
                                    }
                                )
                            )

                            val rocketAlpha by rocketTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = keyframes {
                                        durationMillis = 4500
                                        0f at 0
                                        1f at 500
                                        1f at 3200 
                                        0f at 3500
                                        0f at 4500
                                    }
                                )
                            )

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_neon_rocket),
                                    contentDescription = "Rocket",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .graphicsLayer {
                                            translationY = rocketOffset
                                            alpha = rocketAlpha
                                        }
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // 2. Foreground Elements
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.weight(0.10f))
                                Box(modifier = Modifier.size(80.dp))
                                Spacer(modifier = Modifier.weight(0.05f))

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = motivationPhrase.uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.SansSerif,
                                        color = Color(0xFFE3F2FD),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "SLEEP DURATION: ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = "${hours}h ${minutes}m",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.SansSerif,
                                            color = Color(0xFF90CAF9)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(0.12f))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3F51B5))
                                        .combinedClickable(
                                            onClick = { stopAlarmAndFinish() }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "START MY DAY", 
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                Spacer(modifier = Modifier.weight(0.25f))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dismissAlarm() {
        stopAlarmOnly()
        showSummary.value = true
        // اهتزاز خفيف لتأكيد استلام الأمر
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(100)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && !showSummary.value) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val acceleration = sqrt(x * x + y * y + z * z) - 9.81f
            if (acceleration > 12f) { // عتبة الهزة القوية
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
