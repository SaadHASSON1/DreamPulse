package com.smartsleep.alarm.util

import androidx.compose.ui.graphics.Color
import java.util.Calendar

object ThemeUtils {

    data class ThemeColors(
        val primary: Color,
        val secondary: Color,
        val backgroundGradient: List<Color>,
        val name: String
    )

    fun getCurrentTheme(): ThemeColors {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 6..9 -> ThemeColors( // Morning
                primary = Color(0xFFFFD54F),
                secondary = Color(0xFFF57C00),
                backgroundGradient = listOf(Color(0xFFFFF176), Color(0xFFFFB74D)),
                name = "Morning"
            )
            in 10..16 -> ThemeColors( // Day
                primary = Color(0xFF64B5F6),
                secondary = Color(0xFF1976D2),
                backgroundGradient = listOf(Color(0xFFE3F2FD), Color(0xFF90CAF9)),
                name = "Day"
            )
            in 17..20 -> ThemeColors( // Evening
                primary = Color(0xFFFF8A65),
                secondary = Color(0xFFD84315),
                backgroundGradient = listOf(Color(0xFFFFCCBC), Color(0xFFFF8A65)),
                name = "Evening"
            )
            else -> ThemeColors( // Night
                primary = Color(0xFF7986CB),
                secondary = Color(0xFF283593),
                backgroundGradient = listOf(Color(0xFF1A237E), Color(0xFF000000)),
                name = "Night"
            )
        }
    }

    private val phrases = listOf(
        "Rise and shine!",
        "Ready to conquer the day?",
        "Good morning!",
        "The world is waiting!",
        "Success starts now!",
        "Dream big, live bigger.",
        "Make today count!",
        "You've got this!"
    )

    fun getRandomPhrase(name: String = ""): String {
        return phrases.random()
    }
}
