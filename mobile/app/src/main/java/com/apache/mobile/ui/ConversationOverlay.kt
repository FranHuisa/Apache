package com.apache.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ui.components.ApacheGradient

/**
 * Pantalla del modo conversación manos libres: como una llamada con Apache.
 * El orbe cambia según el estado (escuchando, pensando o hablando).
 */
@Composable
fun ConversationOverlay(chat: ChatViewModel) {
    val state = when {
        chat.isListening -> "Te escucho…"
        chat.isLoading -> "Pensando…"
        chat.isSpeaking -> "Hablando"
        else -> "Un momento…"
    }

    val transition = rememberInfiniteTransition(label = "conversation")
    val speed = when {
        chat.isListening -> 700
        chat.isSpeaking -> 450
        else -> 1200
    }
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (chat.isLoading) 1.06f else 1.18f,
        animationSpec = infiniteRepeatable(tween(speed, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val ring by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07140D), Color(0xFF0B1F24), Color(0xFF050807))))
            // Toques en el fondo no pasan a la pantalla de debajo.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Conversación con Apache", color = Color.White, fontSize = 20.sp)
                Text("Di «para» o «gracias, ya está» para terminar", color = ApacheColors.textMuted, fontSize = 13.sp)
            }

            // Orbe.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                if (chat.isListening || chat.isSpeaking) {
                    Box(
                        modifier = Modifier.size(140.dp).scale(ring).alpha((1.9f - ring) / 0.9f * 0.35f)
                            .clip(CircleShape).background(if (chat.isListening) Color(0xFF1DB954) else Color(0xFF0E7C86))
                    )
                }
                Box(
                    modifier = Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(ApacheGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = Color.Black, fontSize = 60.sp)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(targetState = state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "state") {
                    Text(it, color = ApacheColors.accentLight, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (chat.lastHeard.isNotBlank()) {
                    Text(
                        "«${chat.lastHeard}»", color = ApacheColors.textMuted, fontSize = 15.sp,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (chat.lastAnswer.isNotBlank()) {
                    Text(
                        chat.lastAnswer, color = Color.White, fontSize = 16.sp,
                        textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)).padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0xFFE53935))
                            .clickable { chat.endConversation() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Terminar", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.width(1.dp).height(12.dp))
                Text("Terminar", color = ApacheColors.textMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
