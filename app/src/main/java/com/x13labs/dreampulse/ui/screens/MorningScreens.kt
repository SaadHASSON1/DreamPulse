package com.x13labs.dreampulse.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Text
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.ui.components.EdgeRing
import com.x13labs.dreampulse.ui.components.StarField
import com.x13labs.dreampulse.ui.components.Sun
import com.x13labs.dreampulse.ui.components.formatClock
import com.x13labs.dreampulse.ui.components.holdToConfirm
import com.x13labs.dreampulse.ui.components.rememberHoldProgress
import com.x13labs.dreampulse.ui.theme.Dream
import kotlinx.coroutines.delay

private const val SUN_RISE_DP = 18

/**
 * Sunrise alarm: the screen warms up from night to dawn over 20 s while the sun rises.
 * Hold anywhere to stop; the ring around the edge fills to show how long is left.
 */
@Composable
fun AlarmScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val hold = rememberHoldProgress()

    val sunrise = remember { MutableTransitionState(false).apply { targetState = true } }
    val t = rememberTransition(sunrise, label = "sunrise")
    val bg by t.animateColor({ tween(20_000, easing = LinearEasing) }, label = "bg") { up -> if (up) Dream.Dawn else Dream.DeepSky }
    val rise by t.animateFloat({ tween(20_000, easing = LinearEasing) }, label = "rise") { up -> if (up) 0f else 1f }
    val glow = rememberInfiniteTransition(label = "glow")
    val pulse by glow.animateFloat(0.92f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(10_000) } }

    Box(
        Modifier.fillMaxSize().background(bg)
            .holdToConfirm(hold, durationMs = 1500) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onDismiss()
            }
    ) {
        EdgeRing(
            fraction = hold.value.value, color = Dream.Sun, track = Color.Transparent,
            fullCircle = true, strokeWidth = 6.dp, inset = 3.dp,
        )
        Column(
            Modifier.fillMaxSize().padding(top = 26.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The sun rises inside its own box, which is tall enough for the whole climb,
            // so the offset can never push it onto the clock below.
            Box(Modifier.height((44 + SUN_RISE_DP).dp)) {
                Sun(
                    44.dp,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (SUN_RISE_DP * rise).dp),
                    color = Dream.Sun.copy(alpha = pulse),
                )
            }
            Text(formatClock(context, now), fontSize = 36.sp, fontWeight = FontWeight.Medium, color = Dream.SunLight, maxLines = 1)
            Text(stringResource(R.string.good_morning), fontSize = 14.sp, color = Dream.Sun, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.hold_or_shake), fontSize = 11.sp, color = Dream.SunLight.copy(alpha = 0.8f),
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

/** What the morning screen reports. Nulls mean "sleep was never detected". */
data class SleepSummary(
    val sleptMinutes: Int? = null,
    val latencyMinutes: Int? = null,
    val earlyMinutes: Int = 0,
)

/**
 * Morning summary. Leads with the app's promise: the time it took to fall asleep was
 * not taken out of the sleep you asked for.
 */
@Composable
fun SummaryScreen(summary: SleepSummary, onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        StarField(alpha = 0.5f)
        Column(
            Modifier.fillMaxSize().padding(top = 26.dp, bottom = 62.dp, start = 18.dp, end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val slept = summary.sleptMinutes
            if (slept != null) {
                Text(stringResource(R.string.you_slept), fontSize = 12.sp, color = Dream.MoonMid, maxLines = 1)
                Text(
                    stringResource(R.string.duration_hm, slept / 60, slept % 60),
                    fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Dream.MoonLight, maxLines = 1,
                )
                summary.latencyMinutes?.let { latency ->
                    Text(stringResource(R.string.fell_asleep_in, latency), fontSize = 11.sp, color = Dream.MoonLight, maxLines = 1, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.not_counted), fontSize = 11.sp, color = Dream.Calm, maxLines = 1, textAlign = TextAlign.Center)
                }
                if (summary.earlyMinutes >= 1) {
                    Text(
                        stringResource(R.string.woke_early, summary.earlyMinutes), fontSize = 11.sp, color = Dream.Sun,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(stringResource(R.string.good_morning), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Dream.MoonLight, maxLines = 1)
                Text(stringResource(R.string.no_sleep_detected), fontSize = 11.sp, color = Dream.Muted, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        EdgeButton(
            onClick = onFinish,
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
        ) { Text(stringResource(R.string.start_day), maxLines = 1) }
    }
}
