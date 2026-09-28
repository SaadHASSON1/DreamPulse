package com.x13labs.dreampulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.x13labs.dreampulse.ui.components.Moon
import com.x13labs.dreampulse.ui.theme.Dream

/** One screen of the first-run introduction; the Activity drops the ones already granted. */
enum class OnboardingStep(val title: Int, val body: Int, val action: Int, val skippable: Boolean) {
    WELCOME(R.string.app_name, R.string.ob_welcome_body, R.string.ob_next, false),
    SENSORS(R.string.ob_sensors_title, R.string.ob_sensors_body, R.string.ob_allow, true),
    BACKGROUND(R.string.ob_background_title, R.string.ob_background_body, R.string.ob_allow, true),
    NOTIFICATIONS(R.string.ob_notif_title, R.string.ob_notif_body, R.string.ob_allow, true),
    ALARM_SCREEN(R.string.perm_title, R.string.perm_body, R.string.perm_open, true),
    BATTERY(R.string.samsung_title, R.string.samsung_body, R.string.perm_open, true),
    READY(R.string.ob_ready_title, R.string.ob_ready_body, R.string.ob_begin, false),
}

/**
 * Explains the idea, then asks for each permission on its own screen with the reason next
 * to it, instead of a burst of system dialogs the moment the app opens.
 *
 * [onAction] runs a step (asks for the permission, opens a settings page) and calls its
 * `done` callback when the user may move on, whatever they chose.
 */
@Composable
fun OnboardingScreen(
    steps: List<OnboardingStep>,
    onAction: (step: OnboardingStep, done: () -> Unit) -> Unit,
    onFinish: () -> Unit,
) {
    // Saved by name: the Activity can be recreated while a settings page is open
    var currentName by rememberSaveable { mutableStateOf(steps.first().name) }
    val index = steps.indexOfFirst { it.name == currentName }.takeIf { it >= 0 } ?: steps.lastIndex
    val step = steps[index]
    val next = {
        if (index >= steps.lastIndex) onFinish() else currentName = steps[index + 1].name
    }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(Dream.Sky),
        ) {
            if (step == OnboardingStep.WELCOME || step == OnboardingStep.READY) {
                item { Moon(size = 34.dp) }
            }
            item {
                Text(
                    stringResource(step.title), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    color = Dream.MoonLight, textAlign = TextAlign.Center,
                )
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(step.body), fontSize = 13.sp, color = Dream.Moon, textAlign = TextAlign.Center)
                }
            }
            item {
                Button(
                    onClick = { if (step == OnboardingStep.WELCOME || step == OnboardingStep.READY) next() else onAction(step, next) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
                ) { Text(stringResource(step.action), maxLines = 1) }
            }
            if (step.skippable) {
                item {
                    Button(
                        onClick = next,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Dream.Track, contentColor = Dream.Muted),
                    ) { Text(stringResource(R.string.perm_skip), maxLines = 1) }
                }
            }
            item {
                Text(
                    "${index + 1} / ${steps.size}", fontSize = 10.sp, color = Dream.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
