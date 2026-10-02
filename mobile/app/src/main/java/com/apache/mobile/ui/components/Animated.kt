package com.apache.mobile.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ui.ApacheColors

/** Degradado verde de la marca (el "orbe" de Apache). */
val ApacheGradient = Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF0E7C86)))

/**
 * Avatar de Apache: círculo con degradado y una "A". Si [alive] es true,
 * respira suavemente (mientras piensa o escucha).
 */
@Composable
fun ApacheOrb(size: Dp = 40.dp, alive: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (alive) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbPulse"
    )

    Box(
        modifier = Modifier.size(size).scale(pulse).clip(CircleShape).background(ApacheGradient),
        contentAlignment = Alignment.Center
    ) {
        Text("A", color = Color.Black, fontSize = (size.value * 0.45f).sp)
    }
}

/** Tres puntos que botan: Apache está escribiendo. */
@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")

    Surface(color = ApacheColors.botBubble, shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(500, delayMillis = index * 160, easing = LinearEasing),
                        RepeatMode.Reverse
                    ),
                    label = "dot$index"
                )
                Box(modifier = Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(ApacheColors.accentLight))
            }
        }
    }
}

/**
 * Botón de micrófono. Mientras escucha, unas ondas salen del botón y
 * el icono cambia a "parar".
 */
@Composable
fun MicButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit, size: Dp = 48.dp) {
    val transition = rememberInfiniteTransition(label = "mic")
    val wave by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "micWave"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.7f)) {
        if (listening) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(wave)
                    .alpha((1.7f - wave) / 0.7f * 0.5f)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (listening) Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFB71C1C))) else ApacheGradient
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Parar" else "Hablar",
                tint = Color.Black
            )
        }
    }
}

/** Botón-pastilla para sugerencias rápidas ("¿Qué tengo hoy?"). */
@Composable
fun SuggestionPill(text: String, onClick: () -> Unit) {
    Surface(
        color = ApacheColors.card,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            text,
            color = ApacheColors.accentSoft,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
