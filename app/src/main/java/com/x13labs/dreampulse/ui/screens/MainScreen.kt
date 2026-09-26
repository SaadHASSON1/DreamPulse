package com.x13labs.dreampulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.ui.components.EdgeDial
import com.x13labs.dreampulse.ui.components.StarField
import com.x13labs.dreampulse.ui.components.formatDuration
import com.x13labs.dreampulse.ui.components.formatMinutesOfDay
import com.x13labs.dreampulse.ui.theme.Dream
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel
import com.x13labs.dreampulse.util.AppLanguage
import kotlin.math.roundToInt

private const val MIN_SLEEP = 5
private const val MAX_SLEEP = 720
private const val SLEEP_STEP = 5
private const val DEADLINE_STEP = 15

/**
 * Navigation map
 *  home      pager [Setup | Settings] when idle, Tracking while a session runs
 *  deadline  pushed screen; swipe in from the left edge to go back
 *  language  pushed screen; swipe in from the left edge to go back
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val nav = rememberSwipeDismissableNavController()
    // No global time text: the setup dial runs along the top edge.
    AppScaffold(timeText = {}) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    viewModel,
                    onEditDeadline = { nav.navigate("deadline") },
                    onLanguage = { nav.navigate("language") },
                )
            }
            composable("deadline") { DeadlineScreen(viewModel, onDone = { nav.popBackStack() }) }
            composable("language") { LanguageScreen(onDone = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: MainViewModel, onEditDeadline: () -> Unit, onLanguage: () -> Unit) {
    val isTracking by viewModel.isTrackingState.collectAsState()
    if (isTracking) {
        TrackingScreen(viewModel)
        return
    }
    val pager = rememberPagerState(pageCount = { 2 })
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> SetupScreen(viewModel, onEditDeadline)
            else -> SettingsScreen(viewModel, onEditDeadline, onLanguage)
        }
    }
}

/** Small dots at the top: "there is another page to the side". */
@Composable
private fun PageDots(selected: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(5.dp).clip(CircleShape)
                    .background(if (i == selected) Dream.MoonLight else Dream.Track)
            )
        }
    }
}

@Composable
private fun Chip(text: String, textColor: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .widthIn(max = 124.dp),
    ) {
        Text(text, fontSize = 11.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Setup

@Composable
private fun SetupScreen(viewModel: MainViewModel, onEditDeadline: () -> Unit) {
    val context = LocalContext.current
    val duration by viewModel.sleepDurationMinutes.collectAsState()
    val deadlineOn by viewModel.hardDeadlineEnabled.collectAsState()
    val deadlineMin by viewModel.hardDeadlineMinutes.collectAsState()
    val range = (MAX_SLEEP - MIN_SLEEP).toFloat()

    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        StarField()
        EdgeDial(
            fraction = (duration - MIN_SLEEP) / range,
            onDrag = { f ->
                val raw = MIN_SLEEP + f * range
                viewModel.setDuration(((raw / SLEEP_STEP).roundToInt() * SLEEP_STEP).coerceIn(MIN_SLEEP, MAX_SLEEP))
            },
            onRotaryStep = { step -> viewModel.setDuration((duration + step * SLEEP_STEP).coerceIn(MIN_SLEEP, MAX_SLEEP)) },
        )
        PageDots(0, 2, Modifier.align(Alignment.TopCenter).padding(top = 22.dp))

        // Content lives between the dots (top) and the edge button (bottom): the column's
        // padding reserves both areas, so nothing can overlap them.
        Column(
            Modifier.fillMaxSize().padding(top = 34.dp, bottom = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.sleep_goal), fontSize = 12.sp, color = Dream.Muted, maxLines = 1)
            Text(formatDuration(duration), fontSize = 36.sp, fontWeight = FontWeight.Medium, color = Dream.MoonLight, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Chip(
                text = if (deadlineOn) stringResource(R.string.wake_by, formatMinutesOfDay(context, deadlineMin))
                else stringResource(R.string.wake_by_off),
                textColor = if (deadlineOn) Dream.Sun else Dream.Muted,
                background = Dream.Track,
                onClick = onEditDeadline,
            )
        }

        EdgeButton(
            onClick = { viewModel.startTracking() },
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
        ) {
            Text(stringResource(R.string.start), maxLines = 1)
        }
    }
}

// Wake-by time

@Composable
private fun DeadlineScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val enabled by viewModel.hardDeadlineEnabled.collectAsState()
    val minutes by viewModel.hardDeadlineMinutes.collectAsState()

    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        EdgeDial(
            fraction = minutes / 1440f,
            onDrag = { f ->
                viewModel.setHardDeadlineEnabled(true)
                viewModel.setHardDeadlineMinutes(((f * 1440f / DEADLINE_STEP).roundToInt() * DEADLINE_STEP).coerceAtMost(1440 - DEADLINE_STEP))
            },
            onRotaryStep = { step ->
                viewModel.setHardDeadlineEnabled(true)
                viewModel.setHardDeadlineMinutes(minutes + step * DEADLINE_STEP)
            },
            color = if (enabled) Dream.Sun else Dream.Muted,
        )
        Column(
            Modifier.fillMaxSize().padding(top = 34.dp, bottom = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.wake_by_label), fontSize = 12.sp, color = Dream.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp),
            )
            Text(
                formatMinutesOfDay(context, minutes), fontSize = 34.sp, fontWeight = FontWeight.Medium,
                color = if (enabled) Dream.SunLight else Dream.Muted, maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Chip(
                text = stringResource(if (enabled) R.string.on else R.string.off),
                textColor = if (enabled) Dream.SunLight else Dream.Muted,
                background = if (enabled) Dream.DawnMid else Dream.Track,
                onClick = { viewModel.setHardDeadlineEnabled(!enabled) },
            )
        }
        EdgeButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
        ) { Text(stringResource(R.string.done), maxLines = 1) }
    }
}

// Settings (second pager page)

@Composable
private fun SettingsScreen(viewModel: MainViewModel, onEditDeadline: () -> Unit, onLanguage: () -> Unit) {
    val context = LocalContext.current
    val deadlineOn by viewModel.hardDeadlineEnabled.collectAsState()
    val deadlineMin by viewModel.hardDeadlineMinutes.collectAsState()
    val listState = rememberScalingLazyListState()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val lang = AppLanguage.current(context)

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(Dream.Sky),
        ) {
            item {
                Text(
                    stringResource(R.string.settings), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = Dream.MoonLight, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            item {
                SettingRow(
                    label = stringResource(R.string.wake_by_label),
                    value = if (deadlineOn) formatMinutesOfDay(context, deadlineMin) else stringResource(R.string.off),
                    onClick = onEditDeadline,
                )
            }
            if (AppLanguage.isSupported) {
                item {
                    SettingRow(
                        label = stringResource(R.string.language),
                        value = lang?.let { AppLanguage.nativeName(it) } ?: stringResource(R.string.lang_system),
                        onClick = onLanguage,
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.tagline), fontSize = 12.sp, color = Dream.Moon, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.version, version), fontSize = 11.sp, color = Dream.Muted, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.by_line), fontSize = 11.sp, color = Dream.Muted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Dream.Track, contentColor = Dream.MoonLight),
        secondaryLabel = { Text(value, color = Dream.Moon, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    ) { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

// Language

@Composable
private fun LanguageScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val current = AppLanguage.current(context)
    val listState = rememberScalingLazyListState()
    val options: List<String?> = listOf(null) + AppLanguage.supported

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize().background(Dream.Sky)) {
            item {
                Text(stringResource(R.string.language), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Dream.MoonLight)
            }
            options.forEach { tag ->
                item {
                    val selected = tag == current
                    Button(
                        onClick = {
                            onDone()
                            AppLanguage.set(context, tag)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Dream.MoonDeep else Dream.Track,
                            contentColor = Dream.MoonLight,
                        ),
                    ) {
                        Text(tag?.let { AppLanguage.nativeName(it) } ?: stringResource(R.string.lang_system), maxLines = 1)
                    }
                }
            }
        }
    }
}
