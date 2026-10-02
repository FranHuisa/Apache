package com.apache.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.apache.mobile.data.ChatImage
import com.apache.mobile.data.ChatMessage

/** Pantalla de chat con Apache: mensajes, imágenes, adjuntos y micrófono. */
@Composable
fun ChatScreen(chat: ChatViewModel) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Selector de fotos/archivos del sistema.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { chat.addAttachment(it) }
    }

    // Permiso del micrófono la primera vez.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) chat.toggleListening() else chat.notice = "Sin permiso del micrófono no puedo escucharte."
    }

    LaunchedEffect(chat.messages.size, chat.isLoading) {
        // Índice 0 = separador superior, así que el último mensaje está en messages.size.
        if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.size + if (chat.isLoading) 1 else 0)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Apache", color = ApacheColors.accent, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { chat.newConversation() }, enabled = !chat.isLoading) {
                Text("+ Nueva", color = ApacheColors.accentLight)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            items(chat.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onSaveImage = { chat.saveImage(it) },
                    onOpenSource = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                )
            }

            if (chat.isLoading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ApacheColors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pensando...", color = ApacheColors.textMuted, fontSize = 14.sp)
                    }
                }
            }
        }

        // Adjuntos preparados (tocar para quitar).
        if (chat.pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chat.pendingAttachments) { attachment ->
                    Surface(
                        color = ApacheColors.card,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { chat.removeAttachment(attachment) }
                    ) {
                        Text(
                            "📎 ${attachment.name}  ✕",
                            color = ApacheColors.accentSoft,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        if (chat.isListening) {
            Text(
                "Escuchando...",
                color = ApacheColors.accentLight,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )
        }

        // Entrada de texto.
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { pickFile.launch("*/*") }, enabled = !chat.isLoading) {
                Text("📎", fontSize = 20.sp)
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe a Apache...") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ApacheColors.accent,
                    unfocusedBorderColor = Color(0xFF444444),
                    cursorColor = ApacheColors.accent
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (input.isBlank() && chat.pendingAttachments.isEmpty()) {
                // Sin texto: micrófono.
                FilledIconButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) chat.toggleListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !chat.isLoading,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (chat.isListening) Color(0xFFAA2222) else ApacheColors.accent
                    )
                ) { Text("🎤", fontSize = 18.sp) }
            } else {
                FilledIconButton(
                    onClick = {
                        chat.send(input.trim())
                        input = ""
                    },
                    enabled = !chat.isLoading,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ApacheColors.accent)
                ) { Text("➤", fontSize = 18.sp, color = Color.Black) }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onSaveImage: (ChatImage) -> Unit,
    onOpenSource: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.text.isNotBlank()) {
            Surface(
                color = if (message.isUser) ApacheColors.accent else ApacheColors.botBubble,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 16.dp
                ),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = if (message.isUser) Color.Black else Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }

        if (message.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(message.images.size) { index ->
                    ImageCard(
                        image = message.images[index],
                        position = index + 1,
                        large = message.images.size == 1,
                        onSave = onSaveImage,
                        onOpenSource = onOpenSource
                    )
                }
            }
        }
    }
}

/** Imagen del chat: tocar = guardar en la galería; "Origen" abre la página. */
@Composable
private fun ImageCard(
    image: ChatImage,
    position: Int,
    large: Boolean,
    onSave: (ChatImage) -> Unit,
    onOpenSource: (String) -> Unit
) {
    val width = if (large) 280.dp else 180.dp
    val height = if (large) 200.dp else 135.dp

    Column(modifier = Modifier.width(width)) {
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(RoundedCornerShape(12.dp))
                .background(ApacheColors.botBubble)
                .clickable { onSave(image) }
        ) {
            NetImage(image.url, image.title, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color(0xAA000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(position.toString(), color = Color.White, fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Toca para guardar",
                color = ApacheColors.textFaint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Origen ↗",
                color = ApacheColors.accentLight,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onOpenSource(image.sourceUrl ?: image.url) }
            )
        }
    }
}
