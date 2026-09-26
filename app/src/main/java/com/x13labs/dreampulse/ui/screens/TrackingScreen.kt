package com.x13labs.dreampulse.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.ui.components.EdgeRing
import com.x13labs.dreampulse.ui.components.HoldPill
import com.x13labs.dreampulse.ui.components.Moon
import com.x13labs.dreampulse.ui.components.StarField
import com.x13labs.dreampulse.ui.components.formatClock
import com.x13labs.dreampulse.ui.theme.Dream
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/** Same as SleepMonitorService's smart-wake window. */
private const val SMART_WINDOW_MS = 15 * 60 * 1000L

@Composable
fun TrackingScreen(viewModel: MainViewModel) {
    val info by viewModel.trackingInfo.collectAsState()
    val onBody by viewModel.isOnBody.collectAsState()
    if (info.sleepConfirmed && info.sleepStartTime > 0 && info.targetWakeTime > 0) {
        AsleepScreen(info.sleepStartTime, info.targetWakeTime, onStop = { viewModel.stopTracking() })
    } else {
        WaitingScreen(latestAlarm = info.targetWakeTime, offWrist = !onBody, onStop = { viewModel.stopTracking() })
    }
}

/**
 * Before sleep: a moon with a slow "breathing" halo (4 s in, 6 s out) to follow.
 * Laid out top-to-bottom in one column, so text and the stop pill cannot collide.
 */
@Composable
private fun WaitingScreen(latestAlarm: Long, offWrist: Boolean, onStop: () -> Unit) {
    val context = LocalContext.current
    val breath = rememberInfiniteTransition(label = "breath")
    val halo by breath.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 10_000
                0f at 0 using FastOutSlowInEasing
                1f at 4_000 using FastOutSlowInEasing
                0f at 10_000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo",
    )

    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        StarField(alpha = 0.6f)
        Column(
            Modifier.fillMaxSize().padding(top = 22.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(56.dp)) {
                    val r = size.minDimension / 2f * (0.72f + 0.28f * halo)
                    drawCircle(Dream.MoonNight, radius = r, style = Stroke(width = 1.5.dp.toPx()))
                }
                Moon(34.dp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.waiting_title), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = Dream.MoonLight, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            Text(
                stringResource(if (offWrist) R.string.off_wrist else R.string.breathe_hint),
                fontSize = 11.sp, color = if (offWrist) Dream.Sun else Dream.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            if (latestAlarm > 0) {
                Text(
                    stringResource(R.string.latest_alarm, formatClock(context, latestAlarm)),
                    fontSize = 11.sp, color = Dream.MoonMid, maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            HoldPill(stringResource(R.string.hold_to_stop), onConfirmed = onStop)
        }
    }
}

/**
 * Asleep: nearly black, dim ring counting down to the alarm, with the smart-wake window
 * as a dim amber segment at the end. The wake time is the one big thing on screen.
 */
@Composable
private fun AsleepScreen(sleepStart: Long, wakeAt: Long, onStop: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val total = (wakeAt - sleepStart).coerceAtLeast(1L)
    val elapsed = ((now - sleepStart).toFloat() / total).coerceIn(0f, 1f)
    val hasSmartWindow = total >= 30 * 60 * 1000L
    val windowFrom = if (hasSmartWindow) 1f - SMART_WINDOW_MS.toFloat() / total else null

    Box(Modifier.fillMaxSize().background(Dream.DeepSky)) {
        EdgeRing(
            fraction = elapsed, color = Dream.MoonNight, track = Dream.TrackDim,
            strokeWidth = 4.dp, inset = 8.dp,
            highlightFrom = windowFrom, highlightColor = Dream.DawnMid,
        )
        Column(
            Modifier.fillMaxSize().padding(top = 34.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.asleep_since, formatClock(context, sleepStart)),
                fontSize = 11.sp, color = Dream.MoonMid, maxLines = 1,
            )
            Text(formatClock(context, wakeAt), fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Dream.Moon, maxLines = 1)
            if (hasSmartWindow) {
                Text(
                    stringResource(R.string.smart_wake_from, formatClock(context, wakeAt - SMART_WINDOW_MS)),
                    fontSize = 11.sp, color = Dream.SunDeep, maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            HoldPill(
                stringResource(R.string.hold_to_stop), onConfirmed = onStop,
                container = Dream.TrackDim, textColor = Dream.Muted,
            )
        }
    }
}
