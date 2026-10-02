package com.apache.mobile.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.CalendarEvent
import com.apache.mobile.data.Reminder
import com.apache.mobile.tools.parseTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.forLanguageTag("es-ES"))

/**
 * Horario del día: bloques y eventos ordenados por hora, con alta/edición
 * rápida, "Organizar mi día" con Apache y los recordatorios pendientes.
 */
@Composable
fun ScheduleScreen(chat: ChatViewModel) {
    val app = ApacheApp.get()
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now()) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    var planText by remember { mutableStateOf("") }
    var planReply by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    fun reload() { reloadKey++ }

    LaunchedEffect(date, reloadKey) {
        val (loadedEvents, loadedReminders) = withContext(Dispatchers.IO) {
            app.events.onDay(date) to app.reminders.pending()
        }
        events = loadedEvents
        reminders = loadedReminders
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Horario", color = Color.White, fontSize = 24.sp)
                    Text(
                        date.format(DAY).replaceFirstChar { it.titlecase() } + if (date == LocalDate.now()) " · hoy" else "",
                        color = ApacheColors.textMuted, fontSize = 14.sp
                    )
                }
                TextButton(onClick = { date = date.minusDays(1) }) { Text("◀", color = ApacheColors.accentLight) }
                TextButton(onClick = { date = LocalDate.now() }) { Text("Hoy", color = ApacheColors.accentLight) }
                TextButton(onClick = { date = date.plusDays(1) }) { Text("▶", color = ApacheColors.accentLight) }
            }
        }

        if (events.isEmpty()) {
            item {
                Surface(color = ApacheColors.card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Día libre. Añade un bloque o pídele a Apache que te organice el día.",
                        color = ApacheColors.textMuted, fontSize = 14.sp, modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(events, key = { it.id }) { event ->
            EventCard(event) { editing = event }
        }

        item {
            Button(
                onClick = { creating = true },
                colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
            ) { Text("+ Añadir bloque") }
        }

        // Organizar con Apache.
        item {
            Surface(color = ApacheColors.cardAlt, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Organizar con Apache", color = Color.White, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = planText,
                        onValueChange = { planText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej.: estudiar 3 h, gimnasio y tarde libre desde las 19:00", fontSize = 13.sp) },
                        minLines = 2,
                        enabled = !chat.isLoading
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val day = date
                            val request = planText.trim()
                            if (request.isNotBlank()) {
                                planReply = null
                                chat.send("Organiza mi horario del ${day.format(DAY)} ($day). $request") { reply ->
                                    planReply = reply
                                    planText = ""
                                    reload()
                                }
                            }
                        },
                        enabled = !chat.isLoading && planText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
                    ) { Text(if (chat.isLoading) "Apache está pensando..." else "Organizar mi día") }

                    planReply?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = ApacheColors.accentSoft, fontSize = 13.sp)
                    }
                }
            }
        }

        if (reminders.isNotEmpty()) {
            item { Text("Recordatorios pendientes", color = Color.White, fontSize = 17.sp) }
            items(reminders, key = { "r${it.id}" }) { reminder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⏰ ${reminder.triggerAt.format(DAY_TIME)} · ${reminder.title}",
                        color = ApacheColors.textMuted, fontSize = 14.sp, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { app.reminders.setStatus(reminder.id, "cancelled") }
                            app.alarms.cancelReminder(reminder.id)
                            reload()
                        }
                    }) { Text("Quitar", color = ApacheColors.danger, fontSize = 13.sp) }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (creating || editing != null) {
        BlockDialog(
            date = date,
            event = editing,
            onDismiss = { creating = false; editing = null },
            onChanged = { creating = false; editing = null; reload() }
        )
    }
}

@Composable
private fun EventCard(event: CalendarEvent, onClick: () -> Unit) {
    val ended = (event.endAt ?: event.startAt.plusHours(1)).isBefore(LocalDateTime.now())
    val color = when {
        event.status == "completed" -> ApacheColors.completed
        ended -> ApacheColors.overdue
        else -> ApacheColors.confirmed
    }

    Surface(color = color, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.startAt.format(HOUR) + (event.endAt?.let { "\n${it.format(HOUR)}" } ?: ""),
                color = ApacheColors.accentSoft, fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text((if (event.status == "completed") "✓ " else "") + event.title, color = Color.White, fontSize = 15.sp)
                event.location?.let { Text(it, color = ApacheColors.accentSoft, fontSize = 12.sp) }
            }
        }
    }
}

/** Diálogo para crear o editar un bloque (también completar o eliminar). */
@Composable
private fun BlockDialog(date: LocalDate, event: CalendarEvent?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val app = ApacheApp.get()
    val scope = rememberCoroutineScope()

    val defaultStart = LocalDateTime.now().plusHours(1).withMinute(0)
    var title by remember { mutableStateOf(event?.title.orEmpty()) }
    var start by remember { mutableStateOf((event?.startAt ?: defaultStart).format(HOUR)) }
    var end by remember { mutableStateOf((event?.endAt ?: (event?.startAt ?: defaultStart).plusHours(1)).format(HOUR)) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val startTime = parseTime(start)
        val endTime = parseTime(end)
        when {
            title.isBlank() -> error = "Escribe qué vas a hacer."
            startTime == null || endTime == null -> error = "Las horas van como 09:30."
            !endTime.isAfter(startTime) -> error = "El fin debe ser después del inicio."
            else -> scope.launch {
                withContext(Dispatchers.IO) {
                    val saved = if (event == null) {
                        app.events.create(title, date.atTime(startTime), date.atTime(endTime))
                    } else {
                        app.events.update(event.id, title = title, startAt = date.atTime(startTime), endAt = date.atTime(endTime))
                    }
                    saved?.let { app.alarms.scheduleEvent(it) }
                }
                onChanged()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (event == null) "Nuevo bloque" else "Editar bloque") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("¿Qué vas a hacer?") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Desde") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Hasta") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                error?.let { Text(it, color = ApacheColors.danger, fontSize = 13.sp) }

                if (event != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        if (event.status != "completed") {
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { app.events.setStatus(event.id, "completed") }
                                    app.alarms.cancelEvent(event.id)
                                    onChanged()
                                }
                            }) { Text("✓ Hecho", color = ApacheColors.accentLight) }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { app.events.setStatus(event.id, "cancelled") }
                                app.alarms.cancelEvent(event.id)
                                onChanged()
                            }
                        }) { Text("Eliminar", color = ApacheColors.danger) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("Guardar", color = ApacheColors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = ApacheColors.textMuted) } }
    )
}
