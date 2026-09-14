package com.apache.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.CalendarEventDto
import com.apache.ui.theme.ApacheColors
import com.apache.ui.theme.calendarTextFieldColors
import com.apache.util.calendarDisplayStatus
import com.apache.util.calendarStatusColor
import com.apache.util.calendarStatusTextColor

@Composable
fun CalendarScreen(calendar: CalendarController) {

    LaunchedEffect(Unit) { calendar.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Calendario", color = Color.White, fontSize = 26.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Tus próximos 30 días", color = ApacheColors.textCalendarMuted, fontSize = 14.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { calendar.showCompletedArchive = true }) {
                    Text("Archivo", color = ApacheColors.accentLight)
                }
                TextButton(onClick = { calendar.load() }, enabled = !calendar.isLoading) {
                    Text("Actualizar", color = ApacheColors.accentLight)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CalendarEventForm(calendar)

        calendar.error?.let { error ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = error, color = ApacheColors.dangerSoft, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(text = "Próximos eventos", color = Color.White, fontSize = 19.sp)
        Spacer(modifier = Modifier.height(12.dp))

        CalendarEventList(calendar)
    }

    if (calendar.showCompletedArchive) {
        CompletedArchiveDialog(calendar)
    }

    calendar.eventPendingCancellation?.let { event ->
        CancelEventDialog(calendar, event)
    }
}

@Composable
private fun CalendarEventForm(calendar: CalendarController) {
    Surface(modifier = Modifier.fillMaxWidth(), color = ApacheColors.surfaceCard, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (calendar.editingEventId == null) "Nuevo evento" else "Editar evento",
                    color = Color.White,
                    fontSize = 18.sp
                )

                if (calendar.editingEventId != null) {
                    TextButton(onClick = { calendar.resetForm(); calendar.error = null }) {
                        Text(text = "Cancelar edición", color = ApacheColors.dangerSoft)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = calendar.title,
                onValueChange = { calendar.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                singleLine = true,
                colors = calendarTextFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = calendar.date,
                    onValueChange = { calendar.date = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Fecha") },
                    supportingText = { Text("Ej.: 12/09/2026") },
                    singleLine = true,
                    colors = calendarTextFieldColors()
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = calendar.time,
                    onValueChange = { calendar.time = it },
                    modifier = Modifier.width(130.dp),
                    label = { Text("Hora") },
                    supportingText = { Text("Ej.: 18:30") },
                    singleLine = true,
                    colors = calendarTextFieldColors()
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = calendar.location,
                    onValueChange = { calendar.location = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Lugar (opcional)") },
                    singleLine = true,
                    colors = calendarTextFieldColors()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { calendar.save() },
                enabled = !calendar.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
            ) {
                Text(if (calendar.editingEventId == null) "Añadir al calendario" else "Guardar cambios")
            }
        }
    }
}

@Composable
private fun CalendarEventList(calendar: CalendarController) {
    val activeEvents = calendar.activeEvents

    when {
        calendar.isLoading && activeEvents.isEmpty() -> {
            CircularProgressIndicator(color = ApacheColors.accent)
        }

        activeEvents.isEmpty() -> {
            Surface(modifier = Modifier.fillMaxWidth(), color = ApacheColors.surfaceMuted, shape = RoundedCornerShape(14.dp)) {
                Text(
                    text = "No tienes eventos próximos. Crea el primero arriba.",
                    modifier = Modifier.padding(20.dp),
                    color = ApacheColors.textCalendarSubtle
                )
            }
        }

        else -> {
            activeEvents.forEach { event -> CalendarEventCard(calendar, event) }
        }
    }
}

@Composable
private fun CalendarEventCard(calendar: CalendarController, event: CalendarEventDto) {
    val displayStatus = calendarDisplayStatus(event)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        color = ApacheColors.surfaceCardAlt,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                Surface(
                    color = if (displayStatus == "Atrasado") ApacheColors.overdue else ApacheColors.accent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = event.startAt.replace('T', ' ').take(16),
                        modifier = Modifier.padding(10.dp),
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = event.title, color = Color.White, fontSize = 16.sp)

                    if (!event.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = event.location, color = ApacheColors.textCalendarLocation, fontSize = 13.sp)
                    }
                }

                Surface(color = calendarStatusColor(event), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        text = displayStatus,
                        color = calendarStatusTextColor(event),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ApacheColors.divider)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { calendar.beginEdit(event) }, enabled = !calendar.isLoading) {
                    Text(text = "Editar", color = ApacheColors.accentLight)
                }

                if (event.status != "completed") {
                    Spacer(modifier = Modifier.width(4.dp))

                    TextButton(onClick = { calendar.complete(event) }, enabled = !calendar.isLoading) {
                        Text(text = "Completar", color = ApacheColors.accentSofter)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    TextButton(
                        onClick = { calendar.eventPendingCancellation = event },
                        enabled = !calendar.isLoading
                    ) {
                        Text(text = "Cancelar", color = ApacheColors.dangerSoft)
                    }
                }
            }
        }
    }
}
