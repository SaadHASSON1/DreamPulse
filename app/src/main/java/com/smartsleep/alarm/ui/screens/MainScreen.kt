package com.smartsleep.alarm.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.smartsleep.alarm.domain.model.SleepState
import com.smartsleep.alarm.ui.viewmodel.MainViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val isTracking by viewModel.isTracking.collectAsState()
    val durationMin by viewModel.sleepDurationMinutes.collectAsState()
    val sleepState by viewModel.currentSleepState.collectAsState()
    val currentMotion by viewModel.currentMotion.collectAsState()
    val currentHeartRate by viewModel.currentHeartRate.collectAsState()
    val isOnBody by viewModel.isOnBody.collectAsState()
    
    val context = LocalContext.current
    
    val batteryLevel by remember {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        mutableStateOf(if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0)
    }

    val transitionSpec = remember {
        val duration = 400
        val easing = FastOutSlowInEasing
        ContentTransform(
            targetContentEnter = slideInHorizontally(animationSpec = tween(duration, easing = easing)) { it } + fadeIn(animationSpec = tween(duration)),
            initialContentExit = slideOutHorizontally(animationSpec = tween(duration, easing = easing)) { -it } + fadeOut(animationSpec = tween(duration))
        )
    }
    
    val transitionSpecBack = remember {
        val duration = 400
        val easing = FastOutSlowInEasing
        ContentTransform(
            targetContentEnter = slideInHorizontally(animationSpec = tween(duration, easing = easing)) { -it } + fadeIn(animationSpec = tween(duration)),
            initialContentExit = slideOutHorizontally(animationSpec = tween(duration, easing = easing)) { it } + fadeOut(animationSpec = tween(duration))
        )
    }

    Scaffold {
        val theme = remember { com.smartsleep.alarm.util.ThemeUtils.getCurrentTheme() }
        
        AnimatedContent(
            targetState = isTracking,
            transitionSpec = {
                if (targetState) transitionSpec else transitionSpecBack
            },
            label = "MainTransition"
        ) { tracking ->
            val scrollState = rememberScalingLazyListState()
            
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = theme.backgroundGradient
                        )
                    ),
                state = scrollState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Branding (Always at top)
                item {
                    Text(
                        text = "DREAMPULSE",
                        style = MaterialTheme.typography.title3.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        ),
                        color = Color.White,
                        letterSpacing = 0.sp,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                    )
                }

                if (!tracking) {
                    // --- SETUP STATE ---
                    item {
                        Text(
                            "Hello, Dreamer!",
                            style = MaterialTheme.typography.caption1,
                            color = Color(0xFF64B5F6),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    item {
                        var currentTime by remember { mutableStateOf("") }
                        LaunchedEffect(Unit) {
                            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
                            val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
                            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                            while(true) {
                                currentTime = sdf.format(java.util.Calendar.getInstance().time)
                                kotlinx.coroutines.delay(10000)
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusChip(
                                icon = if (batteryLevel > 20) "🔋" else "⚠️",
                                label = "$batteryLevel%",
                                color = if (batteryLevel > 20) Color(0xFF4CAF50) else Color(0xFFFF5252)
                            )
                            StatusChip(icon = "🕒", label = currentTime, color = Color.White)
                            StatusChip(icon = "🛰️", label = "Active", color = Color(0xFF64B5F6))
                        }
                    }

                    item {
                        Text(
                            "WAKE UP IN",
                            style = MaterialTheme.typography.caption2,
                            color = Color.LightGray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { if (durationMin > 1) viewModel.setDuration(durationMin - 1) },
                                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2C2C2C))
                            ) {
                                Text("-", fontSize = 20.sp, color = Color.White)
                            }
                            
                            Text(
                                "${durationMin / 60}h ${durationMin % 60}m",
                                style = MaterialTheme.typography.title1,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Button(
                                onClick = { if (durationMin < 720) viewModel.setDuration(durationMin + 1) },
                                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2C2C2C))
                            ) {
                                Text("+", fontSize = 20.sp, color = Color.White)
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactButton(label = "-30m", onClick = { if (durationMin > 30) viewModel.setDuration(durationMin - 30) })
                            CompactButton(label = "+30m", onClick = { if (durationMin < 690) viewModel.setDuration(durationMin + 30) })
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.startTracking() },
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .height(52.dp)
                                .padding(bottom = 20.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1976D2))
                        ) {
                            Text("START TRACKING", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }

                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 20.dp, bottom = 40.dp)
                        ) {
                            Text(
                                "X13LABS",
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                } else {
                    // --- TRACKING STATE ---
                    item { PulseIcon() }
                    
                    item {
                        Text(
                            if (sleepState == SleepState.ASLEEP) "Sweet Dreams..." else "Monitoring Sleep...",
                            style = MaterialTheme.typography.title2,
                            color = Color(0xFF64B5F6),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                if (isOnBody) String.format("Motion: %.2f", currentMotion) else "OFF-BODY ⚠️",
                                style = MaterialTheme.typography.caption2,
                                color = if (!isOnBody) Color(0xFFFF5252) else if (currentMotion > 0.1f) Color(0xFF64B5F6) else Color.DarkGray
                            )
                            
                            if (isOnBody && currentHeartRate > 0) {
                                Text(
                                    "💓 ${currentHeartRate.toInt()}",
                                    style = MaterialTheme.typography.caption2,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                    
                    item {
                        Button(
                            onClick = { viewModel.stopTracking() },
                            modifier = Modifier.fillMaxWidth(0.8f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
                        ) {
                            Text("STOP", fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.simulateSleep() },
                            modifier = Modifier.padding(top = 10.dp).height(28.dp),
                            colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2C2C2C))
                        ) {
                            Text("Simulate", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(icon: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF2C2C2C), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(icon, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CompactButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(30.dp).width(64.dp),
        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2C2C2C))
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun PulseIcon() {
    Box(
        modifier = Modifier
            .padding(top = 24.dp)
            .size(50.dp)
            .background(Color(0xFF64B5F6).copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("🌙", fontSize = 24.sp)
    }
}
