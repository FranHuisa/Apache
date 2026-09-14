package com.apache.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.ChatMessage
import com.apache.ui.components.MessageBubble
import com.apache.ui.theme.ApacheColors
import com.apache.ui.voice.VoiceController

@Composable
fun ChatScreen(chat: ChatController, voice: VoiceController) {

    var message by remember { mutableStateOf("") }

    val chatListState = rememberLazyListState()

    fun sendMessage() {
        val text = message.trim()

        if (text.isNotBlank() && !chat.isLoading) {
            message = ""
            chat.sendMessage(text)
        }
    }

    // Mantiene el chat desplazado hasta el último mensaje.
    LaunchedEffect(chat.messages.size, chat.showThinking) {
        if (chat.messages.isNotEmpty()) {
            chatListState.animateScrollToItem(chat.messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        // Título + controles de voz de Apache.
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Asistente", color = Color.White, fontSize = 22.sp)

            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {

                // Control de voz de las respuestas de Apache.
                Surface(
                        color =
                                if (voice.isMuted) {
                                    ApacheColors.dangerBg
                                } else {
                                    ApacheColors.mutedGreenBg
                                },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { voice.toggleMute() }
                ) {
                    Text(
                            text =
                                    if (voice.isMuted) {
                                        "🔇 Voz silenciada"
                                    } else {
                                        "🔊 Voz activada"
                                    },
                            color =
                                    if (voice.isMuted) {
                                        ApacheColors.dangerText
                                    } else {
                                        ApacheColors.accentSoft
                                    },
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Activar/desactivar escucha continua.
                Surface(
                        color =
                                if (voice.listenModeEnabled) {
                                    ApacheColors.dangerBg
                                } else {
                                    ApacheColors.mutedGreenBg
                                },
                        shape = RoundedCornerShape(8.dp),
                        modifier =
                                Modifier.clickable {
                                    if (!voice.isRecording) {
                                        voice.toggleListenMode()
                                    }
                                }
                ) {
                    Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "🎧", fontSize = 16.sp)

                        Text(
                                text =
                                        if (voice.listenModeEnabled) {
                                            "Escucha activa"
                                        } else {
                                            "Modo escucha"
                                        },
                                color =
                                        if (voice.listenModeEnabled) {
                                            ApacheColors.dangerText
                                        } else {
                                            ApacheColors.accentSoft
                                        },
                                fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Conversación.
        LazyColumn(
                state = chatListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(chat.messages) { chatMessage -> MessageBubble(chatMessage) }

            if (chat.showThinking) {
                item { MessageBubble(ChatMessage("Pensando...", false)) }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (voice.listenModeEnabled) {
            Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    color = ApacheColors.listenBannerBg,
                    shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                        text = "Escucha continua activa · Di «Apache, apaga» para detenerla",
                        modifier = Modifier.padding(12.dp),
                        color = ApacheColors.accentSoft,
                        fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Entrada de mensaje.
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .widthIn(max = 900.dp)
                                .align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f).height(64.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    placeholder = { Text(text = "Escribe un mensaje...", fontSize = 16.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                    singleLine = true,
                    enabled = !chat.isLoading && !voice.isRecording,
                    shape = RoundedCornerShape(14.dp),
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = ApacheColors.accent,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    cursorColor = ApacheColors.accent,
                                    focusedPlaceholderColor = Color(0xFF777777),
                                    unfocusedPlaceholderColor = Color(0xFF777777)
                            )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Micrófono.
            Surface(
                    color =
                            if (voice.isRecording) {
                                Color(0xFFAA2222)
                            } else {
                                ApacheColors.mutedGreenBg
                            },
                    shape = RoundedCornerShape(14.dp)
            ) {
                IconButton(
                        onClick = { voice.toggleRecording() },
                        enabled = !chat.isLoading && !voice.listenModeEnabled
                ) { Text(text = "🎤", fontSize = 20.sp) }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                    enabled = !chat.isLoading && !voice.isRecording,
                    onClick = { sendMessage() },
                    modifier = Modifier.height(64.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent)
            ) { Text(text = "Enviar", color = Color.Black, fontSize = 15.sp) }
        }
    }
}
