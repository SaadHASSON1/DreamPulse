package com.x13labs.dreampulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.data.local.NightHistory
import com.x13labs.dreampulse.ui.theme.Dream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Last 7 nights, newest first. Each card: date, slept time with a bar, time to fall asleep. */
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    var nights by remember { mutableStateOf<List<NightHistory.Night>?>(null) }
    LaunchedEffect(Unit) { nights = withContext(Dispatchers.IO) { NightHistory.last(context) } }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(Dream.Sky),
        ) {
            item {
                Text(
                    stringResource(R.string.history_title), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = Dream.MoonLight, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val list = nights
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.history_empty), fontSize = 12.sp, color = Dream.Muted,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            list?.forEach { night -> item { NightCard(night) } }
        }
    }
}

@Composable
private fun NightCard(night: NightHistory.Night) {
    val date = remember(night.sessionStart) {
        java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()).format(java.util.Date(night.sessionStart))
    }
    val slept = night.sleptMinutes
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Dream.Track)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date, fontSize = 12.sp, color = Dream.Muted, maxLines = 1)
            Text(
                if (slept != null) stringResource(R.string.duration_hm, slept / 60, slept % 60)
                else stringResource(R.string.no_sleep_detected),
                fontSize = if (slept != null) 16.sp else 11.sp, fontWeight = FontWeight.Medium,
                color = Dream.MoonLight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (slept != null && night.goalMinutes > 0) {
            // Bar: slept time against the goal (full = goal reached)
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Dream.TrackDim)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth((slept.toFloat() / night.goalMinutes).coerceIn(0f, 1f))
                        .background(Dream.Moon)
                )
            }
        }
        night.latencyMinutes?.let {
            Text(stringResource(R.string.history_latency, it), fontSize = 11.sp, color = Dream.Calm, maxLines = 1)
        }
        night.batteryUsed?.let {
            Text(stringResource(R.string.history_battery, it), fontSize = 11.sp, color = Dream.Muted, maxLines = 1)
        }
    }
}
