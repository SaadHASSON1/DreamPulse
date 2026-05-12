package com.x13labs.dreampulse.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.*
import com.x13labs.dreampulse.domain.model.SleepState
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel

@Composable
fun StarryBackground(modifier: Modifier = Modifier) {
    // Cache star positions — computed once, drawn every frame without recalculation
    val stars = remember {
        val random = java.util.Random(42)
        List(60) {
            Triple(
                random.nextFloat(), // x ratio
                random.nextFloat(), // y ratio
                random.nextFloat() * 0.3f + 0.1f // alpha
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(alpha = 0.99f)
            .drawBehind {
                stars.forEach { (xRatio, yRatio, alpha) ->
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 1.1f,
                        center = androidx.compose.ui.geometry.Offset(
                            xRatio * size.width,
                            yRatio * size.height
                        )
                    )
                }
            }
    )
}

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val isTracking by viewModel.isTrackingState.collectAsState()
    val context = LocalContext.current

    // Show permission screen only if:
    // 1. Android 14+ device
    // 2. Permission not yet granted
    // 3. User hasn't dismissed it before
    val needsPermission = remember {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val dismissed = prefs.getBoolean("perm_screen_dismissed", false)
        if (dismissed) return@remember false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            !nm.canUseFullScreenIntent()
        } else false
    }

    var showPermissionScreen by remember { mutableStateOf(needsPermission) }
    var showWelcome by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        StarryBackground()

        if (showPermissionScreen) {
            PermissionSetupScreen(
                onDismiss = { showPermissionScreen = false }
            )
        } else {
            Crossfade(
                targetState = if (showWelcome) "welcome" else if (isTracking) "tracking" else "setup",
                animationSpec = tween(500, easing = LinearEasing),
                label = "MainScreenTransition"
            ) { state ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        "welcome"  -> WelcomeScreen(onStart = { showWelcome = false })
                        "setup"    -> SetupScreen(viewModel = viewModel, onBack = { showWelcome = true })
                        "tracking" -> MonitoringScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Permission Setup Screen — shown once on Android 14+
// ─────────────────────────────────────────────────────────────
@Composable
fun PermissionSetupScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Helper to permanently dismiss this screen
    fun dismiss() {
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("perm_screen_dismissed", true).apply()
        onDismiss()
    }

    // Poll every second — auto-dismiss when permission is granted
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                if (nm.canUseFullScreenIntent()) {
                    dismiss()
                    break
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "perm_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "perm_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Warning icon
        Text(
            text = "⚠️",
            fontSize = 26.sp,
            modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Permission Required",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5252),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "Enable \"Full-screen intents\" so the alarm screen appears automatically.",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Open Settings button
        Button(
            onClick = {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            },
            modifier = Modifier.fillMaxWidth(0.82f).height(38.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252)),
            shape = RoundedCornerShape(19.dp)
        ) {
            Text("Open Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Skip — saves flag so this screen never appears again
        Text(
            text = "Skip",
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.clickable { dismiss() }
        )
    }
}


@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val angleState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val context = LocalContext.current
    val appIcon = remember {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Single shared pulse animation instead of 8 separate ones
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            repeat(8) { i ->
                val startAngle = i * 45f
                val currentAngle = (startAngle + angleState) % 360f
                val rad = Math.toRadians(currentAngle.toDouble())
                // Stagger the pulse per dot using a phase offset
                val dotAlpha = (pulse + i * 0.08f).coerceIn(0.2f, 1f)
                val x = (Math.cos(rad) * 36).dp
                val y = (Math.sin(rad) * 36).dp
                Box(
                    modifier = Modifier
                        .offset(x, y)
                        .size(if (i % 2 == 0) 3.5.dp else 2.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = dotAlpha))
                )
            }

            if (appIcon != null) {
                Image(
                    bitmap = appIcon!!,
                    contentDescription = "Logo",
                    modifier = Modifier.size(62.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DreamPulse",
                style = MaterialTheme.typography.title3,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sleep Smarter",
                style = MaterialTheme.typography.caption1,
                color = Color(0xFF90CAF9),
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.65f).height(38.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5))
        ) {
            Text("START", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Text(
            text = "by Saad HASSON (X13LABS)",
            style = MaterialTheme.typography.caption2,
            color = Color(0xFF90CAF9).copy(alpha = 0.6f),
            fontSize = 8.sp
        )
    }
}

@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val durationMin by viewModel.sleepDurationMinutes.collectAsState()
    val lastSleepSummary by viewModel.lastSleepSummary.collectAsState()
    val deadlineEnabled by viewModel.hardDeadlineEnabled.collectAsState()
    val deadlineMinutes by viewModel.hardDeadlineMinutes.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.14f))
        
        Text(
            text = "WAKE ME UP IN",
            style = MaterialTheme.typography.caption1,
            color = Color(0xFF90CAF9),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(0.07f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallBtn("-30") { viewModel.setDuration((durationMin - 30).coerceAtLeast(1)) }
                Spacer(modifier = Modifier.height(6.dp))
                SmallBtn("-") { viewModel.setDuration((durationMin - 1).coerceAtLeast(1)) }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02dh", durationMin / 60),
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%02dm", durationMin % 60),
                    fontSize = 22.sp,
                    color = Color(0xFF90CAF9),
                    fontWeight = FontWeight.Bold
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallBtn("+30") { viewModel.setDuration((durationMin + 30).coerceAtMost(720)) }
                Spacer(modifier = Modifier.height(6.dp))
                SmallBtn("+") { viewModel.setDuration((durationMin + 1).coerceAtMost(720)) }
            }
        }

        Spacer(modifier = Modifier.weight(0.05f))

        // Hard Deadline Row
        if (!deadlineEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { viewModel.toggleHardDeadline() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⏰ Set deadline",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(0.75f).height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { viewModel.adjustHardDeadline(-15) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "⏰ ${String.format("%02d:%02d", deadlineMinutes / 60, deadlineMinutes % 60)}",
                    fontSize = 13.sp,
                    color = Color(0xFFFFAB40),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.toggleHardDeadline() }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { viewModel.adjustHardDeadline(15) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.05f))

        Button(
            onClick = { viewModel.startTracking() },
            modifier = Modifier.fillMaxWidth(0.5f).height(44.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "START", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "TRACKING", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (lastSleepSummary != "No data available") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = lastSleepSummary, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.weight(0.06f))
    }
}

@Composable
fun MonitoringScreen(
    viewModel: MainViewModel
) {
    val sleepState by viewModel.currentSleepState.collectAsState()
    val heartRate by viewModel.currentHeartRate.collectAsState()
    val isOnBody by viewModel.isOnBody.collectAsState()

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.15f))

        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.x13labs.dreampulse.R.drawable.ic_realistic_moon),
                contentDescription = "Moon",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        // Optimization: Fix the layer to prevent the parent from shaking
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.weight(0.12f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 4.dp)
        ) {
            Text(
                text = if (sleepState == SleepState.ASLEEP) "Sweet Dreams" else "Monitoring Sleep",
                fontSize = 16.sp,
                color = if (sleepState == SleepState.ASLEEP) Color(0xFF81C784) else Color(0xFF90CAF9),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💓", fontSize = 10.sp)
                    Text(
                        text = " ${heartRate.toInt()}", 
                        style = MaterialTheme.typography.caption2, 
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isOnBody) Color(0xFF81C784) else Color.Gray))
                    Text(
                        text = " Active", 
                        style = MaterialTheme.typography.caption2, 
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.18f))

        Row(
            modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.stopTracking() },
                modifier = Modifier.height(38.dp).weight(1f).padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFB71C1C)),
                shape = RoundedCornerShape(19.dp)
            ) {
                Text(text = "STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = { viewModel.simulateSleep() },
                modifier = Modifier.size(38.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.1f)),
                shape = CircleShape
            ) {
                Text(text = "SM", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))
    }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.08f)),
        shape = CircleShape
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
