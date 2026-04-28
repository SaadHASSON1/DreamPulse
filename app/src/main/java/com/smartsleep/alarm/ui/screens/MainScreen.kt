package com.smartsleep.alarm.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.smartsleep.alarm.domain.model.SleepState
import com.smartsleep.alarm.ui.viewmodel.MainViewModel

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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val isTracking by viewModel.isTracking.collectAsState()
    val durationMin by viewModel.sleepDurationMinutes.collectAsState()
    val sleepState by viewModel.currentSleepState.collectAsState()
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

    val transitionSpec = fadeIn(animationSpec = tween(600)) with fadeOut(animationSpec = tween(600))

    Scaffold {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            StarryBackground()
            
            AnimatedContent(
                targetState = isTracking,
                transitionSpec = { transitionSpec },
                label = "MainTransition"
            ) { tracking ->
                val scrollState = rememberScalingLazyListState()
                
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                style = MaterialTheme.typography.caption1.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                ),
                                color = Color(0xFF90CAF9),
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
                                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusChip(
                                    icon = if (batteryLevel > 20) "🔋" else "⚠️",
                                    label = "$batteryLevel%",
                                    color = if (batteryLevel > 20) Color(0xFF81C784) else Color(0xFFFF7043)
                                )
                                StatusChip(icon = "🕒", label = currentTime, color = Color.White)
                            }
                        }

                        item {
                            Text(
                                "WAKE UP IN",
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                    item {
                        // Main Time Adjustment Row (+/- 1m)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { if (durationMin > 1) viewModel.setDuration(durationMin - 1) },
                                modifier = Modifier.size(36.dp),
                                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("-", fontSize = 20.sp, color = Color.White)
                            }
                            
                            Text(
                                "${durationMin / 60}h ${durationMin % 60}m",
                                style = MaterialTheme.typography.title2,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Button(
                                onClick = { if (durationMin < 720) viewModel.setDuration(durationMin + 1) },
                                modifier = Modifier.size(36.dp),
                                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("+", fontSize = 20.sp, color = Color.White)
                            }
                        }
                    }

                    item {
                        // Quick Adjustment Row (+/- 30m)
                        Row(
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { if (durationMin > 30) viewModel.setDuration(durationMin - 30) },
                                modifier = Modifier.height(28.dp).width(44.dp),
                                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("-30m", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                            
                            Button(
                                onClick = { if (durationMin <= 690) viewModel.setDuration(durationMin + 30) },
                                modifier = Modifier.height(28.dp).width(44.dp),
                                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("+30m", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }

                        item {
                            Button(
                                onClick = { viewModel.startTracking() },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(top = 8.dp)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF64B5F6)
                                ),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Text("START TRACKING", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                        
                        item {
                            // --- FEATURE TIP (Smart Education) ---
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .fillMaxWidth(0.85f)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "✨ PRO TIP ✨",
                                    style = MaterialTheme.typography.caption2.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 8.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color(0xFFFFD54F)
                                )
                                Text(
                                    "SHAKE WATCH TO STOP ALARM",
                                    style = MaterialTheme.typography.caption2.copy(fontSize = 9.sp),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        item {
                            Text(
                                "by Saad HASSON (X13LABS)",
                                style = MaterialTheme.typography.caption2.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 16.dp, bottom = 28.dp)
                            )
                        }

                    } else {
                        // --- TRACKING STATE ---
                        item { PulseIcon() }
                        
                        item {
                            Text(
                                if (sleepState == SleepState.ASLEEP) "Sweet Dreams..." else "Monitoring Sleep...",
                                style = MaterialTheme.typography.title3.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF90CAF9),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                StatusChip(
                                    icon = "💓", 
                                    label = if (isOnBody && currentHeartRate > 0) "${currentHeartRate.toInt()}" else "--",
                                    color = Color(0xFFFF5252)
                                )
                                StatusChip(
                                    icon = "🛰️", 
                                    label = if (isOnBody) "Active" else "Off",
                                    color = if (isOnBody) Color(0xFF64B5F6) else Color.Gray
                                )
                            }
                        }
                        
                        item {
                            Button(
                                onClick = { viewModel.stopTracking() },
                                modifier = Modifier.fillMaxWidth(0.7f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828)),
                                shape = RoundedCornerShape(19.dp)
                            ) {
                                Text("STOP", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }

                        item {
                            Button(
                                onClick = { viewModel.simulateSleep() },
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .fillMaxWidth(0.8f)
                                    .height(36.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
                                colors = ButtonDefaults.secondaryButtonColors(
                                    backgroundColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    "Simulation Mode", 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Add extra space at the end for circular screens
                        item { Spacer(modifier = Modifier.height(65.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PulseIcon() {
    Box(
        modifier = Modifier
            .padding(top = 16.dp)
            .size(65.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle Glow Behind the Moon
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF64B5F6).copy(alpha = 0.05f), CircleShape)
        )
        
        Image(
            painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_realistic_moon),
            contentDescription = "Realistic Moon",
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun StatusChip(icon: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(icon, fontSize = 11.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
