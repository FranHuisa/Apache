package com.apache.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.ChatMessage
import com.apache.ui.theme.ApacheColors

/**
 * Dibuja una burbuja de mensaje, alineada según su remitente.
 *
 * Si el mensaje trae imágenes (respuestas de Apache con searchImages), se
 * dibujan debajo de la burbuja de texto.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onConfirm: ((confirmationId: String, approved: Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {

            if (message.text.isNotBlank()) {
                Surface(
                    color = if (message.isUser) ApacheColors.accent else ApacheColors.botBubble,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = if (message.attachmentNames.isEmpty()) {
                                message.text
                            } else {
                                message.text + "\n📎 " + message.attachmentNames.joinToString(", ")
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = if (message.isUser) Color.Black else Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Acción pendiente de confirmar: botones Sí / No.
            val confirmationId = message.confirmationId
            if (confirmationId != null && onConfirm != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConfirm(confirmationId, true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ApacheColors.accent,
                            contentColor = Color.Black
                        )
                    ) { Text("Sí, hazlo") }

                    OutlinedButton(onClick = { onConfirm(confirmationId, false) }) {
                        Text("No, cancelar", color = ApacheColors.dangerSoft)
                    }
                }
            }

            if (message.images.isNotEmpty()) {
                if (message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                ChatImages(message.images)
            }
        }
    }
}
