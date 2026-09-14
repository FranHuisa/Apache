package com.apache.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.ChatMessage
import com.apache.ui.theme.ApacheColors

/** Dibuja una burbuja de mensaje, alineada según su remitente. */
@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) ApacheColors.accent else ApacheColors.botBubble,
            shape = RoundedCornerShape(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (message.isUser) Color.Black else Color.White,
                    fontSize = 15.sp
                )
            }
        }
    }
}
