package com.apache.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.NotificationDto
import com.apache.ui.theme.ApacheColors

/** Avisos no leídos, pensados para superponerse en la esquina superior derecha. */
@Composable
fun NotificationsOverlay(notifications: List<NotificationDto>, onDismiss: (Long) -> Unit) {
    Column(modifier = Modifier.width(320.dp)) {
        notifications.forEach { notification ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                color = ApacheColors.accent,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = notification.title, color = Color.Black, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = notification.message, color = Color(0xFF0A2E17), fontSize = 13.sp)
                    }

                    Text(
                        text = "✕",
                        color = Color.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onDismiss(notification.id) }
                    )
                }
            }
        }
    }
}
