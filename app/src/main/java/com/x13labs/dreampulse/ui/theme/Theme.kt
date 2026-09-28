package com.x13labs.dreampulse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * One palette for the whole app: a cool moonlight family for the night, and a warm
 * sunrise family for the morning. Night screens stay dim so they don't wake the user.
 */
object Dream {
    // Night
    val Sky = Color(0xFF0B1026)          // screen background
    val DeepSky = Color(0xFF05070F)      // asleep screen, nearly off
    val Track = Color(0xFF1E2548)        // ring tracks, chips
    val TrackDim = Color(0xFF141A33)
    val Moon = Color(0xFFAFA9EC)         // main accent
    val MoonLight = Color(0xFFEEEDFE)    // primary text
    val MoonMid = Color(0xFF7F77DD)      // labels
    val MoonDeep = Color(0xFF534AB7)     // filled buttons
    val MoonNight = Color(0xFF3C3489)    // dim accent while asleep
    val Muted = Color(0xFF8A8FB0)        // secondary text

    // Morning
    val Dawn = Color(0xFF412402)
    val DawnMid = Color(0xFF633806)
    val Sun = Color(0xFFFAC775)
    val SunLight = Color(0xFFFAEEDA)
    val SunDeep = Color(0xFF854F0B)

    // Status
    val Calm = Color(0xFF5DCAA5)         // "not counted" — the app's promise
    val Danger = Color(0xFFB3261E)
    val DangerLight = Color(0xFFF09595)
}

private val DreamColorScheme = ColorScheme(
    primary = Dream.Moon,
    primaryDim = Dream.MoonMid,
    primaryContainer = Dream.MoonDeep,
    onPrimary = Dream.Sky,
    onPrimaryContainer = Dream.MoonLight,
    secondary = Dream.Sun,
    secondaryContainer = Dream.DawnMid,
    onSecondary = Dream.Dawn,
    onSecondaryContainer = Dream.SunLight,
    surfaceContainerLow = Dream.TrackDim,
    surfaceContainer = Dream.Track,
    surfaceContainerHigh = Dream.Track,
    onSurface = Dream.MoonLight,
    onSurfaceVariant = Dream.Muted,
    background = Dream.Sky,
    onBackground = Dream.MoonLight,
    error = Dream.DangerLight,
)

@Composable
fun DreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DreamColorScheme, content = content)
}
