package com.smartsleep.alarm.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
fun StarryBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = java.util.Random(42)
        List(60) { // تقليل العدد قليلاً لضمان سلاسة فائقة
            Triple(random.nextFloat(), random.nextFloat(), random.nextFloat() * 0.3f + 0.1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                stars.forEach { (x, y, alpha) ->
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 1.1f,
                        center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height)
                    )
                }
            }
    )
}

enum class AppScreen {
    WELCOME, SETUP, TRACKING
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val isTracking by viewModel.isTracking.collectAsState()
    val durationMin by viewModel.sleepDurationMinutes.collectAsState()
    val sleepState by viewModel.currentSleepState.collectAsState()
    val currentHeartRate by viewModel.currentHeartRate.collectAsState()
    val isOnBody by viewModel.isOnBody.collectAsState()
    
    // إدارة حالة الشاشة الحالية
    val currentScreen = remember { mutableStateOf(AppScreen.WELCOME) }

    // مزامنة الشاشة مع حالة التتبع الفعلية
    LaunchedEffect(isTracking) {
        if (isTracking) {
            currentScreen.value = AppScreen.TRACKING
        } else if (currentScreen.value == AppScreen.TRACKING) {
            currentScreen.value = AppScreen.SETUP
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // الخلفية مرسومة مرة واحدة ومخزنة في الكاش لضمان 60 هرتز
        StarryBackground()
        
        AnimatedContent(
            targetState = currentScreen.value,
            transitionSpec = {
                // تأثير Morph راقي (تلاشي مع تكبير و تصغير)
                if (targetState.ordinal > initialState.ordinal) {
                    (scaleIn(animationSpec = tween(400, easing = FastOutSlowInEasing), initialScale = 0.9f) + fadeIn(animationSpec = tween(400))) with
                            (scaleOut(animationSpec = tween(400, easing = FastOutSlowInEasing), targetScale = 1.1f) + fadeOut(animationSpec = tween(400)))
                } else {
                    (scaleIn(animationSpec = tween(400, easing = FastOutSlowInEasing), initialScale = 1.1f) + fadeIn(animationSpec = tween(400))) with
                            (scaleOut(animationSpec = tween(400, easing = FastOutSlowInEasing), targetScale = 0.9f) + fadeOut(animationSpec = tween(400)))
                }
            },
            label = "MorphTransition"
        ) { screen ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (screen) {
                    AppScreen.WELCOME -> WelcomeScreen(
                        onStart = { currentScreen.value = AppScreen.SETUP }
                    )
                    AppScreen.SETUP -> SetupScreen(
                        durationMin = durationMin,
                        onDurationChange = { viewModel.setDuration(it) },
                        onStartTracking = { viewModel.startTracking() },
                        onBack = { currentScreen.value = AppScreen.WELCOME }
                    )
                    AppScreen.TRACKING -> MonitoringScreen(
                        sleepState = sleepState,
                        heartRate = currentHeartRate,
                        isOnBody = isOnBody,
                        onStop = { viewModel.stopTracking() },
                        onSimulate = { viewModel.simulateSleep() }
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // أنيميشن الدوران العام لمدارات النجوم
    val angleState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // تحميل اللوغو بطريقة برمجية آمنة جداً لمنع الانهيار
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
        // Orbital Stars + Logo
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Stars Animation (remains same)
            repeat(8) { i ->
                val startAngle = i * 45f
                val currentAngle = (startAngle + angleState) % 360f
                val rad = Math.toRadians(currentAngle.toDouble())
                val pulse by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000 + (i * 200), easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val x = (Math.cos(rad) * 36).dp
                val y = (Math.sin(rad) * 36).dp
                Box(
                    modifier = Modifier
                        .offset(x, y)
                        .size(if (i % 2 == 0) 3.5.dp else 2.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = pulse))
                )
            }

            // 1. اللوغو بحجم متناسق داخل مدار النجوم
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "Official Logo",
                    modifier = Modifier.size(62.dp) // حجم مثالي ليكون داخل مدار النجوم
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DreamPulse",
                style = MaterialTheme.typography.title3,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "Sleep Smarter",
                style = MaterialTheme.typography.caption1,
                color = Color(0xFF90CAF9),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.65f).height(38.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5))
        ) {
            Text("START", fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
        }

        Text(
            text = "by Saad HASSON (X13LABS)",
            style = MaterialTheme.typography.caption2,
            color = Color(0xFF90CAF9).copy(alpha = 0.6f),
            fontSize = 8.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
fun SetupScreen(
    durationMin: Int,
    onDurationChange: (Int) -> Unit,
    onStartTracking: () -> Unit,
    onBack: () -> Unit
) {
    // تفعيل الرجوع للخلف عند ضغط زر الرجوع في الساعة
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // موازنة دقيقة ليكون الوقت في المركز الهندسي
        Spacer(modifier = Modifier.weight(0.2f))
        
        Text(
            text = "WAKE ME UP IN",
            style = MaterialTheme.typography.caption1,
            color = Color(0xFF90CAF9),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.weight(0.1f))

        // 2. لوحة التحكم المتطورة (توزيع واسع و متناسق)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp) // تقريب الأزرار من الحواف بأمان
        ) {
            // الجناح الأيسر
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SmallBtn("-30") { onDurationChange((durationMin - 30).coerceAtLeast(1)) }
                Spacer(modifier = Modifier.height(12.dp))
                SmallBtn("-") { onDurationChange((durationMin - 1).coerceAtLeast(1)) }
            }

            // القلب (الوقت بحجم أكبر و تناسق عمودي)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02dh", durationMin / 60),
                    fontSize = 26.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%02dm", durationMin % 60),
                    fontSize = 26.sp,
                    color = Color(0xFF90CAF9),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 28.sp
                )
            }

            // الجناح الأيمن
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SmallBtn("+30") { onDurationChange((durationMin + 30).coerceAtMost(720)) }
                Spacer(modifier = Modifier.height(12.dp))
                SmallBtn("+") { onDurationChange((durationMin + 1).coerceAtMost(720)) }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // 3. زر START TRACKING فائق الرشاقة (Pill Style)
        Button(
            onClick = onStartTracking,
            modifier = Modifier
                .fillMaxWidth(0.5f) // تقليل العرض للنصف ليكون نحيفاً جداً
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "START", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 12.sp
                )
                Text(
                    text = "TRACKING", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))
    }
}

@Composable
fun MonitoringScreen(
    sleepState: SleepState,
    heartRate: Float,
    isOnBody: Boolean,
    onStop: () -> Unit,
    onSimulate: () -> Unit
) {
    // أنيميشن النبض "التنفسي" للقمر ليعطي شعوراً بالحياة
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

        // 1. القمر (ينبض مباشرة بدون حلقات)
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.smartsleep.alarm.R.drawable.ic_realistic_moon),
                contentDescription = "Moon",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale) // تصحيح الاسم
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.weight(0.12f))

        // 2. حالة النوم و البيانات (نظيفة بدون خلفية)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 4.dp)
        ) {
            Text(
                text = if (sleepState == SleepState.ASLEEP) "Sweet Dreams" else "Monitoring Sleep",
                fontSize = 16.sp, // حجم أكبر للوضوح و الفخامة
                color = if (sleepState == SleepState.ASLEEP) Color(0xFF81C784) else Color(0xFF90CAF9),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
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
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isOnBody) Color(0xFF81C784) else Color.Gray))
                    Text(
                        text = " Active", 
                        style = MaterialTheme.typography.caption2, 
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.18f))

        // 3. أزرار التحكم الرشيقة (تم تصغيرها بطلبك)
        Row(
            modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .height(38.dp)
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFB71C1C)),
                shape = RoundedCornerShape(19.dp)
            ) {
                Text(
                    text = "STOP", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
            
            Button(
                onClick = onSimulate,
                modifier = Modifier.size(38.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color.White.copy(alpha = 0.1f)),
                shape = CircleShape
            ) {
                Text(
                    text = "SM", 
                    fontSize = 8.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
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

@Composable
fun PulseIcon() {
    Box(
        modifier = Modifier
            .padding(top = 16.dp)
            .size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
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
