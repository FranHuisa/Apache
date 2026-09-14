package com.apache.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.CalendarEventDto
import com.apache.ui.theme.ApacheColors

@Composable
fun CompletedArchiveDialog(calendar: CalendarController) {
    AlertDialog(
        onDismissRequest = { if (!calendar.isLoading) calendar.showCompletedArchive = false },
        containerColor = ApacheColors.surfaceCardAlt,
        titleContentColor = androidx.compose.ui.graphics.Color.White,
        textContentColor = ApacheColors.textCalendarBody,
        title = { Text(text = "Archivo") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                val completedEvents = calendar.completedEvents

                if (completedEvents.isEmpty()) {
                    Text(text = "No hay eventos terminados todavía.", color = ApacheColors.textCalendarSubtle)
                } else {
                    LazyColumn {
                        items(completedEvents, key = { it.id }) { event ->
                            ArchivedEventRow(event)
                            HorizontalDivider(color = ApacheColors.divider)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { calendar.showCompletedArchive = false }) {
                Text(text = "Cerrar", color = ApacheColors.accentLight)
            }
        }
    )
}

@Composable
private fun ArchivedEventRow(event: CalendarEventDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = event.title, color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = event.startAt.replace('T', ' ').take(16),
            color = ApacheColors.textArchivedDate,
            fontSize = 13.sp
        )

        if (!event.location.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = event.location, color = ApacheColors.textArchivedDate, fontSize = 12.sp)
        }
    }
}

@Composable
fun CancelEventDialog(calendar: CalendarController, event: CalendarEventDto) {
    AlertDialog(
        onDismissRequest = { if (!calendar.isLoading) calendar.eventPendingCancellation = null },
        containerColor = ApacheColors.surfaceCardAlt,
        titleContentColor = androidx.compose.ui.graphics.Color.White,
        textContentColor = ApacheColors.textCalendarBody,
        title = { Text(text = "Cancelar evento") },
        text = {
            Text(
                text = "¿Quieres cancelar «${event.title}»? El evento se conservará en el historial, " +
                    "pero dejará de aparecer en tu calendario."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    calendar.eventPendingCancellation = null
                    calendar.cancel(event)
                },
                enabled = !calendar.isLoading
            ) {
                Text(text = "Cancelar evento", color = ApacheColors.dangerSoft)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { calendar.eventPendingCancellation = null },
                enabled = !calendar.isLoading
            ) {
                Text(text = "Volver", color = ApacheColors.accentLight)
            }
        }
    )
}
