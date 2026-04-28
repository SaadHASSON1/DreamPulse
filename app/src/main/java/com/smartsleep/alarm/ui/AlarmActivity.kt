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
import androidx.compose.foundation.border
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
import kotlinx.coroutines.delay

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
class AlarmActivity : ComponentActivity() {
    
    @javax.inject.Inject lateinit var sleepRepository: com.smartsleep.alarm.data.repository.SleepRepository

    private var showSummary = mutableStateOf(false)
    private var sleepStartTimeState = mutableStateOf(0L)
    private var userNameState = mutableStateOf("")

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
            val infiniteTransition = rememberInfiniteTransition()
            val theme = remember { ThemeUtils.getCurrentTheme() }
            val motivationPhrase = remember { ThemeUtils.getRandomPhrase(userNameState.value) }
            
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    StarryBackground()
                    
                    if (!showSummary.value) {
                        // 1. CONTENT AREA with high safety margin
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 90.dp) // Absolute safety margin
                                .padding(horizontal = 20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                                // Pulsing Aura (Sun Glow)
                                Box(
                                    modifier = Modifier
                                        .size(75.dp)
                                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                        .background(Color(0xFFFFD54F).copy(alpha = pulseAlpha), CircleShape)
                                )
                                
                                // REALISTIC SUN SPHERE (Forced Clip & Crop)
                                Image(
                                    painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_cosmic_sunrise),
                                    contentDescription = "Realistic Sun",
                                    modifier = Modifier.size(60.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                "WAKE UP",
                                style = MaterialTheme.typography.title2.copy(
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color(0xFFFF8A80)
                            )
                            
                            Text(
                                "Time to start your day!",
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }

                        // 3. HIGHER, NARROWER PILL BUTTON
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp) 
                                .fillMaxWidth(0.78f)
                                .height(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.2.dp, Color(0xFFFF8A80).copy(alpha = 0.4f), CircleShape)
                                .combinedClickable(
                                    onClick = { 
                                        android.widget.Toast.makeText(this@AlarmActivity, "HOLD TO DISMISS", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onLongClick = {
                                        stopAlarmOnly()
                                        showSummary.value = true 
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "HOLD TO DISMISS", 
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        val startTime = sleepStartTimeState.value
                        val durationMs = if (startTime > 0 && startTime < now) {
                            now - startTime
                        } else {
                            0L
                        }
                        val hours = durationMs / (3600 * 1000)
                        val minutes = (durationMs % (3600 * 1000)) / 60000

                        // --- SUMMARY SCREEN (Mirrored Layout) ---
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 90.dp) 
                                .padding(horizontal = 20.dp)
                        ) {
                            // Vitality Icon (Mirrored Size)
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(75.dp)
                                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                        .background(Color(0xFF4FC3F7).copy(alpha = pulseAlpha), CircleShape)
                                )
                                Image(
                                    painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_vitality_core),
                                    contentDescription = "Vitality Core",
                                    modifier = Modifier.size(60.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                motivationPhrase.uppercase(), // ALL CAPS AS REQUESTED
                                textAlign = TextAlign.Center,
                                color = Color(0xFFE3F2FD), 
                                fontWeight = FontWeight.ExtraBold, 
                                style = MaterialTheme.typography.title2.copy(
                                    lineHeight = 18.sp,
                                    fontSize = 15.sp
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                "You rested for ${hours}h ${minutes}m",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // UNIFIED PILL BUTTON (Mirrored Placement & Size)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                                .fillMaxWidth(0.78f)
                                .height(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.2.dp, Color(0xFF4FC3F7).copy(alpha = 0.4f), CircleShape)
                                .combinedClickable(
                                    onClick = { stopAlarmAndFinish() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "START MY DAY", 
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }

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
}
