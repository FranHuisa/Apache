package com.apache.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Misma paleta que Apache de escritorio (ApacheColors). */
object ApacheColors {
    val background = Color(0xFF121212)
    val surface = Color(0xFF181818)
    val card = Color(0xFF1D2923)
    val cardAlt = Color(0xFF202A24)
    val accent = Color(0xFF1DB954)
    val accentLight = Color(0xFF6FE19A)
    val accentSoft = Color(0xFFA7E8BE)
    val botBubble = Color(0xFF242424)
    val textMuted = Color(0xFFAAAAAA)
    val textFaint = Color(0xFF777777)
    val danger = Color(0xFFFF9B9B)
    val completed = Color(0xFF2E7D5A)
    val overdue = Color(0xFF8A5A2B)
    val confirmed = Color(0xFF1E5E3A)
}

private val scheme = darkColorScheme(
    primary = ApacheColors.accent,
    onPrimary = Color.Black,
    secondary = ApacheColors.accentLight,
    background = ApacheColors.background,
    onBackground = Color.White,
    surface = ApacheColors.surface,
    onSurface = Color.White,
    surfaceVariant = ApacheColors.card,
    onSurfaceVariant = ApacheColors.textMuted,
    error = ApacheColors.danger
)

@Composable
fun ApacheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
