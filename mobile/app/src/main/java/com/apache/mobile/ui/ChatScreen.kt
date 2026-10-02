package com.apache.mobile.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.apache.mobile.data.ChatImage
import com.apache.mobile.data.ChatMessage
import com.apache.mobile.ui.components.ApacheGradient
import com.apache.mobile.ui.components.ApacheOrb
import com.apache.mobile.ui.components.MicButton
import com.apache.mobile.ui.components.SuggestionPill
import com.apache.mobile.ui.components.TypingDots

private val SUGGESTIONS = listOf(
    "¿Qué tengo hoy?",
    "Organiza mi día",
    "¿Qué tiempo hace?",
    "Pon música",
    "Recuérdame beber agua en 1 hora",
    "Enséñame un ajolote"
)

/** Pantalla de chat con Apache: mensajes animados, imágenes, adjuntos y voz. */
@Composable
fun ChatScreen(chat: ChatViewModel, onMic: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { chat.addAttachment(it) }
    }

    LaunchedEffect(chat.messages.size, chat.isLoading) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Cabecera: avatar que "respira" y estado.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApacheOrb(size = 40.dp, alive = chat.isLoading || chat.isListening)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Apache", color = Color.White, fontSize = 18.sp)
                Text(
                    when {
                        chat.isListening -> "Escuchando…"
                        chat.isLoading -> "Pensando…"
                        else -> "En línea"
                    },
                    color = if (chat.isLoading || chat.isListening) ApacheColors.accentLight else ApacheColors.textFaint,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { chat.newConversation() }, enabled = !chat.isLoading) {
                Icon(Icons.Filled.AddComment, contentDescription = "Nueva conversación", tint = ApacheColors.accentLight)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chat.messages, key = { it.id }) { message ->
                AnimatedMessage(
                    message = message,
                    onSaveImage = { chat.saveImage(it) },
                    onOpenSource = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    onCopy = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Apache", text))
                        chat.notice = "Copiado"
                    }
                )
            }

            if (chat.isLoading) {
                item(key = "typing") { TypingDots() }
            }

            // Sugerencias cuando la conversación está empezando.
            if (!chat.isLoading && chat.messages.size <= 2) {
                item(key = "suggestions") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(SUGGESTIONS) { suggestion -> SuggestionPill(suggestion) { chat.send(suggestion) } }
                    }
                }
            }
        }

        // Adjuntos preparados (tocar la X para quitar).
        if (chat.pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chat.pendingAttachments) { attachment ->
                    Surface(color = ApacheColors.card, shape = RoundedCornerShape(50)) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = ApacheColors.accentSoft, modifier = Modifier.size(14.dp))
                            Text(attachment.name, color = ApacheColors.accentSoft, fontSize = 12.sp, maxLines = 1, modifier = Modifier.widthIn(max = 160.dp))
                            IconButton(onClick = { chat.removeAttachment(attachment) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = ApacheColors.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Barra de escritura.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = ApacheColors.card, shape = RoundedCornerShape(26.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickFile.launch("*/*") }, enabled = !chat.isLoading) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Adjuntar", tint = ApacheColors.textMuted)
                    }
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Pregúntale a Apache…", color = ApacheColors.textFaint) },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ApacheColors.accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }

            if (input.isBlank() && chat.pendingAttachments.isEmpty()) {
                MicButton(listening = chat.isListening, enabled = !chat.isLoading, onClick = onMic)
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ApacheGradient)
                        .clickable(enabled = !chat.isLoading) {
                            chat.send(input.trim())
                            input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color.Black)
                }
            }
        }
    }
}

/** Mensaje que entra deslizándose (desde la derecha si es tuyo, desde la izquierda si es de Apache). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimatedMessage(
    message: ChatMessage,
    onSaveImage: (ChatImage) -> Unit,
    onOpenSource: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(250)) + slideInHorizontally(tween(300)) { width -> if (message.isUser) width / 3 else -width / 3 }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            if (message.text.isNotBlank()) {
                val shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (message.isUser) 18.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 18.dp
                )
                Box(
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(shape)
                        .background(
                            if (message.isUser) Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF17A34A)))
                            else Brush.linearGradient(listOf(ApacheColors.botBubble, ApacheColors.botBubble))
                        )
                        // Mantener pulsado = copiar el texto.
                        .combinedClickable(onClick = {}, onLongClick = { onCopy(message.text) })
                ) {
                    Text(
                        text = message.text,
                        color = if (message.isUser) Color.Black else Color.White,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (message.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(message.images.size) { index ->
                        ImageCard(message.images[index], index + 1, message.images.size == 1, onSaveImage, onOpenSource)
                    }
                }
            }
        }
    }
}

/** Imagen del chat que aparece con un pequeño zoom. Botones para guardar y ver el origen. */
@Composable
private fun ImageCard(
    image: ChatImage,
    position: Int,
    large: Boolean,
    onSave: (ChatImage) -> Unit,
    onOpenSource: (String) -> Unit
) {
    val width = if (large) 280.dp else 190.dp
    val height = if (large) 210.dp else 145.dp
    val state = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(visibleState = state, enter = fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.85f)) {
        Box(modifier = Modifier.size(width, height).clip(RoundedCornerShape(16.dp)).background(ApacheColors.botBubble)) {
            NetImage(image.url, image.title, Modifier.fillMaxSize())

            // Número, para poder decir "guarda la 2".
            Box(
                modifier = Modifier.padding(8.dp).size(24.dp).clip(CircleShape).background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) { Text(position.toString(), color = Color.White, fontSize = 12.sp) }

            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoundAction(Icons.Filled.Download, "Guardar") { onSave(image) }
                RoundAction(Icons.Filled.OpenInNew, "Origen") { onOpenSource(image.sourceUrl ?: image.url) }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xCC000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
