package com.x13labs.dreampulse.ui.screens

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.util.CrashLog
import com.x13labs.dreampulse.ui.theme.Dream

data class BatteryInfo(val level: Int, val charging: Boolean)

fun readBattery(context: Context): BatteryInfo = runCatching {
    val bm = context.getSystemService(BatteryManager::class.java)
    BatteryInfo(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), bm.isCharging)
}.getOrDefault(BatteryInfo(0, false))

/** Shown instead of starting when the battery might not last until the alarm. */
@Composable
fun LowBatteryScreen(onStartAnyway: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val level = remember { readBattery(context).level }
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize().background(Dream.Sky)) {
            item {
                Text(
                    stringResource(R.string.low_battery_title, level), fontSize = 16.sp,
                    fontWeight = FontWeight.Medium, color = Dream.Sun, textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    stringResource(R.string.low_battery_body), fontSize = 13.sp, color = Dream.Moon,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            item {
                Button(
                    onClick = onCancel, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
                ) { Text(stringResource(R.string.cancel), maxLines = 1) }
            }
            item {
                Button(
                    onClick = onStartAnyway, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Dream.Track, contentColor = Dream.Muted),
                ) { Text(stringResource(R.string.start_anyway), maxLines = 1) }
            }
        }
    }
}

/** The saved crash reports, so the user can photograph them and send them over. */
@Composable
fun CrashLogScreen(onCleared: () -> Unit) {
    val context = LocalContext.current
    // The last few thousand characters: the newest crash, readable on a watch
    val text = remember { CrashLog.read(context).takeLast(4000) }
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize().background(Dream.Sky)) {
            item {
                Text(stringResource(R.string.crash_log), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Dream.MoonLight)
            }
            item {
                Text(
                    stringResource(R.string.crash_log_hint), fontSize = 11.sp, color = Dream.Moon,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            item {
                Text(
                    text, fontSize = 9.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace,
                    color = Dream.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
            item {
                Button(
                    onClick = { CrashLog.clear(context); onCleared() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Dream.Track, contentColor = Dream.MoonLight),
                ) { Text(stringResource(R.string.crash_log_clear), maxLines = 1) }
            }
        }
    }
}
