package com.apache.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta de colores de Apache, centralizada para no repetir literales hexadecimales por toda la UI. */
object ApacheColors {
    val background = Color(0xFF121212)
    val sidebarBackground = Color(0xFF181818)
    val surfaceCard = Color(0xFF1D2923)
    val surfaceCardAlt = Color(0xFF202A24)
    val surfaceMuted = Color(0xFF181F1B)
    val divider = Color(0xFF334039)

    val accent = Color(0xFF1DB954)
    val accentLight = Color(0xFF6FE19A)
    val accentSoft = Color(0xFFA7E8BE)
    val accentSofter = Color(0xFF9DE8B8)

    val botBubble = Color(0xFF242424)
    val textMuted = Color(0xFFAAAAAA)
    val textFaint = Color(0xFF666666)
    val textCalendarMuted = Color(0xFF9AA5A0)
    val textCalendarBody = Color(0xFFCCCCCC)
    val textCalendarSubtle = Color(0xFFB7C2BB)
    val textCalendarLocation = Color(0xFFAAC5B1)
    val textArchivedDate = Color(0xFF8FA59A)

    val danger = Color(0xFF9D3030)
    val dangerSoft = Color(0xFFFF9B9B)
    val dangerBg = Color(0xFF3A2020)
    val dangerText = Color(0xFFE5A0A0)

    val mutedGreenBg = Color(0xFF1E2A22)
    val listenBannerBg = Color(0xFF173D29)

    val overdue = Color(0xFF8A5A2B)
    val overdueText = Color(0xFFFFD39A)
    val completed = Color(0xFF2E7D5A)
    val completedText = Color(0xFFB8F0D0)
    val cancelled = Color(0xFF6A3030)
    val cancelledText = Color(0xFFFFB5B5)
    val confirmed = Color(0xFF1E5E3A)
}

/** Colores compartidos por los `OutlinedTextField` del formulario de calendario. */
@Composable
fun calendarTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = androidx.compose.ui.graphics.Color.White,
    unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
    focusedBorderColor = ApacheColors.accent,
    unfocusedBorderColor = Color(0xFF4A5A50),
    focusedLabelColor = ApacheColors.accentLight,
    unfocusedLabelColor = ApacheColors.textCalendarSubtle,
    cursorColor = ApacheColors.accent
)
