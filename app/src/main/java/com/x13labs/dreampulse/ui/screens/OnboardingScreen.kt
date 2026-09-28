package com.x13labs.dreampulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Text
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.ui.components.Moon
import com.x13labs.dreampulse.ui.components.StarField
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
 * Laid out like the setup screen: the text in the middle and the main action as the edge
 * button, always in the same place and at full strength ("Skip" is a small link above it).
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
    val primary = { if (step == OnboardingStep.WELCOME || step == OnboardingStep.READY) next() else onAction(step, next) }
    val showMoon = step == OnboardingStep.WELCOME || step == OnboardingStep.READY

    Box(Modifier.fillMaxSize().background(Dream.Sky)) {
        StarField()
        // Between the top of the round screen and the edge button; scrolls only if a
        // translation is too long for the space.
        Column(
            Modifier.fillMaxSize()
                .padding(start = 26.dp, end = 26.dp, top = 30.dp, bottom = 64.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showMoon) {
                Moon(size = 30.dp)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                stringResource(step.title), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = Dream.MoonLight, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(step.body), fontSize = 12.sp, color = Dream.Moon, textAlign = TextAlign.Center)
            if (step.skippable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.perm_skip), fontSize = 12.sp, color = Dream.Muted,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = next)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        EdgeButton(
            onClick = primary,
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(containerColor = Dream.MoonDeep, contentColor = Dream.MoonLight),
        ) { Text(stringResource(step.action), maxLines = 1) }
    }
}
