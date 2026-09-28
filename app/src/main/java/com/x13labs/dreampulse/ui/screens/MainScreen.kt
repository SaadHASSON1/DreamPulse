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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.wear.compose.material3.PickerGroup
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.rememberPickerState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.ui.components.EdgeRing
import com.x13labs.dreampulse.ui.components.StarField
import com.x13labs.dreampulse.ui.components.formatDuration
import com.x13labs.dreampulse.ui.components.formatMinutesOfDay
import com.x13labs.dreampulse.ui.theme.Dream
import com.x13labs.dreampulse.ui.viewmodel.MainViewModel
import com.x13labs.dreampulse.util.AppLanguage
import java.time.LocalTime

private const val MIN_SLEEP = 5
private const val MAX_HOURS = 12
private const val MINUTE_STEP = 5

/**
 * Navigation map
 *  home      pager [Setup | History | Settings] when idle, Tracking while a session runs
 *  duration  wheel pickers for hours and minutes
 *  deadline  system time picker for the wake-by time
 *  language  language list
 * Pushed screens go back with a swipe in from the left edge.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val nav = rememberSwipeDismissableNavController()
    AppScaffold(timeText = {}) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    viewModel,
                    onEditDuration = { nav.navigate("duration") },
                    onEditDeadline = { nav.navigate("deadline") },
                    onLanguage = { nav.navigate("language") },
                )
            }
            composable("duration") { DurationScreen(viewModel, onDone = { nav.popBackStack() }) }
            composable("deadline") { DeadlineScreen(viewModel, onDone = { nav.popBackStack() }) }
            composable("language") { LanguageScreen(onDone = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: MainViewModel,
    onEditDuration: () -> Unit,
    onEditDeadline: () -> Unit,
    onLanguage: () -> Unit,
) {
    val isTracking by viewModel.isTrackingState.collectAsState()
    if (isTracking) {
        TrackingScreen(viewModel)
        return
    }
    val pager = rememberPagerState(pageCount = { 3 })
    // All three pages are light: compose them up front so the first swipe doesn't stall
    // while the neighbouring page is built mid-gesture.
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 2) { page ->
        when (page) {
            0 -> SetupScreen(viewModel, onEditDuration, onEditDeadline)
            1 -> HistoryScreen()
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
private fun Chip(text: String, textColor: Color, background: Color, onClick: () -> Unit) {
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

/**
 * The ring only shows the chosen duration (no dragging: a thin ring under a finger was
 * hard to control and fought with page swipes). Tap the number to change it.
 */
@Composable
private fun SetupScreen(viewModel: MainViewModel, onEditDuration: () -> Unit, onEditDeadline: () -> Unit) {
    val context = LocalContext.current
    val duration by viewModel.sleepDurationMinutes.collectAsState()
    val deadlineOn by viewModel.hardDeadlineEnabled.collectAsState()
    val deadlineMin by viewModel.hardDeadlineMinutes.collectAsState()

    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        StarField()
        EdgeRing(fraction = { duration / (MAX_HOURS * 60f) }, color = Dream.Moon, track = Dream.Track, knob = true)
        PageDots(0, 3, Modifier.align(Alignment.TopCenter).padding(top = 22.dp))

        // Content lives between the dots (top) and the edge button (bottom): the column's
        // padding reserves both areas, so nothing can overlap them.
        Column(
            Modifier.fillMaxSize().padding(top = 34.dp, bottom = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onEditDuration)
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.sleep_goal), fontSize = 12.sp, color = Dream.Muted, maxLines = 1)
                Text(formatDuration(duration), fontSize = 36.sp, fontWeight = FontWeight.Medium, color = Dream.MoonLight, maxLines = 1)
                Text(stringResource(R.string.tap_to_change), fontSize = 10.sp, color = Dream.MoonMid, maxLines = 1)
            }
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

// Duration: two wheels, like the watch's own timer and alarm apps

@Composable
private fun DurationScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val current = viewModel.sleepDurationMinutes.collectAsState().value
    val hours = rememberPickerState(initialNumberOfOptions = MAX_HOURS + 1, initiallySelectedIndex = (current / 60).coerceIn(0, MAX_HOURS))
    val minutes = rememberPickerState(initialNumberOfOptions = 60 / MINUTE_STEP, initiallySelectedIndex = (current % 60) / MINUTE_STEP)
    var selected by remember { mutableIntStateOf(0) }
    val hoursLabel = stringResource(R.string.hours)
    val minutesLabel = stringResource(R.string.minutes)

    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        Column(
            Modifier.fillMaxSize().padding(top = 26.dp, bottom = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.sleep_goal), fontSize = 12.sp, color = Dream.Muted, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            // Numbers read left-to-right (hours:minutes) in every language
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                PickerGroup(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    selectedPickerState = if (selected == 0) hours else minutes,
                    autoCenter = false,
                ) {
                    PickerGroupItem(
                        pickerState = hours,
                        selected = selected == 0,
                        onSelected = { selected = 0 },
                        modifier = Modifier.width(64.dp),
                        contentDescription = { "${hours.selectedOptionIndex} $hoursLabel" },
                    ) { index, isSelected ->
                        WheelNumber(index, isSelected)
                    }
                    Text(":", fontSize = 30.sp, color = Dream.MoonLight, modifier = Modifier.padding(horizontal = 2.dp))
                    PickerGroupItem(
                        pickerState = minutes,
                        selected = selected == 1,
                        onSelected = { selected = 1 },
                        modifier = Modifier.width(64.dp),
                        contentDescription = { "${minutes.selectedOptionIndex * MINUTE_STEP} $minutesLabel" },
                    ) { index, isSelected ->
                        WheelNumber(index * MINUTE_STEP, isSelected)
                    }
                }
            }
        }
        EdgeButton(
            onClick = {
                val total = hours.selectedOptionIndex * 60 + minutes.selectedOptionIndex * MINUTE_STEP
                viewModel.setDuration(total.coerceAtLeast(MIN_SLEEP))
                onDone()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
        ) { Text(stringResource(R.string.done), maxLines = 1) }
    }
}

@Composable
private fun WheelNumber(value: Int, selected: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            String.format(java.util.Locale.ROOT, "%02d", value),
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Dream.MoonLight else Dream.Muted,
        )
    }
}

// Wake-by time: the standard system time picker (12/24 h follows the watch setting)

@Composable
private fun DeadlineScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val minutes = viewModel.hardDeadlineMinutes.collectAsState().value
    TimePicker(
        initialTime = LocalTime.of(minutes / 60, minutes % 60),
        onTimePicked = { t ->
            viewModel.setHardDeadlineMinutes(t.hour * 60 + t.minute)
            viewModel.setHardDeadlineEnabled(true)
            onDone()
        },
    )
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
                SwitchButton(
                    checked = deadlineOn,
                    onCheckedChange = { viewModel.setHardDeadlineEnabled(it) },
                    modifier = Modifier.fillMaxWidth(),
                    secondaryLabel = {
                        Text(formatMinutesOfDay(context, deadlineMin), color = Dream.Moon, maxLines = 1)
                    },
                ) { Text(stringResource(R.string.wake_by_label), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            item {
                SettingRow(
                    label = stringResource(R.string.change_time),
                    value = formatMinutesOfDay(context, deadlineMin),
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
