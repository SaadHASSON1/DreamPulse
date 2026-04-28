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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.smartsleep.alarm.ui.theme.SmartSleepTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.remember
import com.smartsleep.alarm.util.ThemeUtils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

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
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContent {
            val infiniteTransition = rememberInfiniteTransition()
            val theme = remember { ThemeUtils.getCurrentTheme() }
            val motivationPhrase = remember { ThemeUtils.getRandomPhrase(userNameState.value) }
            
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = if (showSummary.value) theme.backgroundGradient else listOf(Color(0xFF1A1A1A), Color(0xFF000000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!showSummary.value) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                        .background(Color(0xFFFF5252).copy(alpha = pulseAlpha), androidx.compose.foundation.shape.CircleShape)
                                )
                                DangerIcon()
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "WAKE UP",
                                style = MaterialTheme.typography.display1,
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.ExtraBold
                            )
                            
                            Text(
                                "Time to start your day!",
                                style = MaterialTheme.typography.caption1,
                                color = Color.LightGray
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // زر DISMISS مع حماية الضغطة المطولة
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(52.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFFFF5252))
                                    .combinedClickable(
                                        onClick = { 
                                            // إشعار بسيط لإخبار المستخدم بالضغط المطول
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
                                    fontSize = 13.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("SHAKE TO STOP", style = MaterialTheme.typography.caption2, color = Color.DarkGray)
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

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                        ) {
                            Text(motivationPhrase, 
                                 textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                 color = Color.White, 
                                 fontWeight = FontWeight.ExtraBold, 
                                 style = MaterialTheme.typography.body1)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                "You rested for\n${hours}h ${minutes}m",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.caption1,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { stopAlarmAndFinish() },
                                modifier = Modifier.fillMaxWidth(0.9f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                            ) {
                                Text("DONE", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DangerIcon() {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color(0xFFFF5252).copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🔔", fontSize = 32.sp)
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
        
        // إغلاق كل شيء والعودة لواجهة الساعة الأساسية
        finishAffinity()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_STEM_1 || 
            keyCode == KeyEvent.KEYCODE_STEM_2 || keyCode == KeyEvent.KEYCODE_STEM_3) {
            if (!showSummary.value) {
                stopAlarmOnly()
                showSummary.value = true
            } else {
                stopAlarmAndFinish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
