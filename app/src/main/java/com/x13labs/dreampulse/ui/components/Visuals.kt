package com.x13labs.dreampulse.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.x13labs.dreampulse.ui.theme.Dream
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Faint, static stars. Deliberately sparse and dim: this is a night screen. */
@Composable
fun StarField(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val stars = remember {
        val r = java.util.Random(7)
        List(28) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat() * 0.25f + 0.08f) }
    }
    Canvas(modifier.fillMaxSize()) {
        stars.forEach { (x, y, a) ->
            drawCircle(Color.White.copy(alpha = a * alpha), radius = 1.2f, center = Offset(x * size.width, y * size.height))
        }
    }
}

/** A flat crescent: a disc with a second, background-coloured disc cut over it. */
@Composable
fun Moon(size: Dp, modifier: Modifier = Modifier, color: Color = Dream.Moon, background: Color = Dream.Sky) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(color, radius = r)
        drawCircle(background, radius = r * 0.86f, center = Offset(center.x + r * 0.42f, center.y - r * 0.26f))
    }
}

/** A flat sun: a disc with eight short rays. */
@Composable
fun Sun(size: Dp, modifier: Modifier = Modifier, color: Color = Dream.Sun) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(color, radius = r * 0.5f)
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            val from = Offset(center.x + (r * 0.68f * cos(a)).toFloat(), center.y + (r * 0.68f * sin(a)).toFloat())
            val to = Offset(center.x + (r * 0.95f * cos(a)).toFloat(), center.y + (r * 0.95f * sin(a)).toFloat())
            drawLine(color, from, to, strokeWidth = r * 0.12f, cap = StrokeCap.Round)
        }
    }
}

/**
 * Rings hug the screen edge with a gap centred at the bottom, where the edge button sits,
 * so a ring and the button never overlap.
 */
object RingGeometry {
    const val GAP_DEGREES = 120f
    const val START = 90f + GAP_DEGREES / 2f      // Compose angles: 0 = 3 o'clock, clockwise
    const val SWEEP = 360f - GAP_DEGREES
}

@Composable
fun EdgeRing(
    fraction: () -> Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    inset: Dp = 6.dp,
    knob: Boolean = false,
    fullCircle: Boolean = false,
    highlightFrom: Float? = null,     // optional segment [highlightFrom, 1] in highlightColor
    highlightColor: Color = Dream.SunDeep,
) {
    Canvas(modifier.fillMaxSize()) {
        val sw = strokeWidth.toPx()
        val pad = inset.toPx() + sw / 2f
        val arcSize = Size(size.width - 2 * pad, size.height - 2 * pad)
        val topLeft = Offset(pad, pad)
        val start = if (fullCircle) -90f else RingGeometry.START
        val sweep = if (fullCircle) 360f else RingGeometry.SWEEP
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        drawArc(track, start, sweep, false, topLeft, arcSize, style = stroke)
        if (highlightFrom != null && highlightFrom < 1f) {
            drawArc(highlightColor, start + sweep * highlightFrom, sweep * (1f - highlightFrom), false, topLeft, arcSize, style = stroke)
        }
        val f = fraction().coerceIn(0f, 1f)
        if (f > 0f) drawArc(color, start, sweep * f, false, topLeft, arcSize, style = stroke)
        if (knob) {
            val a = Math.toRadians((start + sweep * f).toDouble())
            val rr = arcSize.width / 2f
            drawCircle(
                Dream.MoonLight, radius = sw * 0.9f,
                center = Offset(center.x + (rr * cos(a)).toFloat(), center.y + (rr * sin(a)).toFloat())
            )
        }
    }
}

/**
 * Press-and-hold state: holding animates 0 to 1; releasing early rewinds to 0.
 * The action fires on RELEASE after a full hold, never while the finger is still down:
 * otherwise the screen changes under the finger and the lift lands as a tap on whatever
 * replaced it (e.g. stop tracking, then the same touch hits Start).
 */
class HoldProgress internal constructor(val value: Animatable<Float, AnimationVector1D>)

@Composable
fun rememberHoldProgress(): HoldProgress = remember { HoldProgress(Animatable(0f)) }

fun Modifier.holdToConfirm(
    hold: HoldProgress,
    durationMs: Int,
    onFilled: () -> Unit = {},
    onConfirmed: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        coroutineScope {
            detectTapGestures(onPress = {
                val a = hold.value
                val job = launch {
                    a.animateTo(1f, tween(((1f - a.value) * durationMs).toInt(), easing = LinearEasing))
                    onFilled()   // e.g. a haptic "you can let go now"
                }
                val released = tryAwaitRelease()
                if (released && a.value >= 1f) {
                    onConfirmed()
                    launch { a.snapTo(0f) }
                } else {
                    job.cancel()
                    launch { a.animateTo(0f, tween(250)) }
                }
            })
        }
    }
)

/** Pill that fills from the reading-start side while held (RTL-aware via fillMaxWidth). */
@Composable
fun HoldPill(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Dream.Track,
    fill: Color = Dream.Danger,
    textColor: Color = Dream.MoonLight,
    durationMs: Int = 1200,
) {
    val hold = rememberHoldProgress()
    val view = LocalView.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .width(148.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .drawBehind {
                val w = size.width * hold.value.value
                if (w > 0f) drawRect(fill, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
            }
            .holdToConfirm(
                hold, durationMs,
                onFilled = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) },
                onConfirmed = onConfirmed,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** Clock text in Western digits (clear on a small screen in every app language). */
fun formatClock(context: android.content.Context, millis: Long): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).format(java.util.Date(millis))
}

fun formatMinutesOfDay(context: android.content.Context, minutes: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minutes / 60)
        set(java.util.Calendar.MINUTE, minutes % 60)
    }
    return formatClock(context, cal.timeInMillis)
}

fun formatDuration(minutes: Int): String = String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)
