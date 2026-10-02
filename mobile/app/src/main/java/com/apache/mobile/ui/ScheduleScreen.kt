package com.apache.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.CalendarEvent
import com.apache.mobile.data.Reminder
import com.apache.mobile.tools.parseTime
import com.apache.mobile.ui.components.ApacheGradient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val SPANISH = Locale.forLanguageTag("es-ES")
private val DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", SPANISH)
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d, HH:mm", SPANISH)

/**
 * Horario: tira de días para elegir fecha, línea de tiempo del día con la
 * hora actual marcada, deslizar un bloque para completarlo (→) o borrarlo (←),
 * y "Organizar mi día" con Apache.
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

    fun setStatus(event: CalendarEvent, status: String) {
        scope.launch {
            withContext(Dispatchers.IO) { app.events.setStatus(event.id, status) }
            app.alarms.cancelEvent(event.id)
            chat.notice = if (status == "completed") "✓ ${event.title}" else "Eliminado: ${event.title}"
            reload()
        }
    }

    LaunchedEffect(date, reloadKey) {
        val loaded = withContext(Dispatchers.IO) { app.events.onDay(date) to app.reminders.pending() }
        events = loaded.first
        reminders = loaded.second
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp)) {
                    Text("Horario", color = Color.White, fontSize = 26.sp)
                    Text(
                        date.format(DAY).replaceFirstChar { it.titlecase() } + if (date == LocalDate.now()) " · hoy" else "",
                        color = ApacheColors.textMuted, fontSize = 14.sp
                    )
                }
            }

            item { DayStrip(selected = date, onSelect = { date = it }) }

            // Línea de tiempo.
            if (events.isEmpty()) {
                item {
                    Surface(
                        color = ApacheColors.card, shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text(
                            "Día libre 🌿 Toca + para añadir un bloque o deja que Apache te lo organice.",
                            color = ApacheColors.textMuted, fontSize = 14.sp, modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                val now = LocalDateTime.now()
                val nowIndex = if (date == LocalDate.now()) events.indexOfFirst { it.startAt.isAfter(now) }.let { if (it < 0) events.size else it } else -1

                events.forEachIndexed { index, event ->
                    if (index == nowIndex) item(key = "now") { NowMarker() }
                    item(key = event.id) {
                        SwipeableEvent(
                            event = event,
                            isLast = index == events.lastIndex,
                            onClick = { editing = event },
                            onComplete = { setStatus(event, "completed") },
                            onDelete = { setStatus(event, "cancelled") }
                        )
                    }
                }
                if (nowIndex == events.size) item(key = "now") { NowMarker() }
            }

            // Organizar con Apache.
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF16402A), Color(0xFF0F2E3A))))
                        .animateContentSize()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ApacheColors.accentLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Organizar con Apache", color = Color.White, fontSize = 17.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = planText,
                            onValueChange = { planText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej.: estudiar 3 h, gimnasio y tarde libre desde las 19:00", fontSize = 13.sp) },
                            minLines = 2,
                            enabled = !chat.isLoading,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(ApacheGradient)
                                .clickable(enabled = !chat.isLoading && planText.isNotBlank()) {
                                    val day = date
                                    val request = planText.trim()
                                    planReply = null
                                    chat.send("Organiza mi horario del ${day.format(DAY)} ($day). $request") { reply ->
                                        planReply = reply
                                        planText = ""
                                        reload()
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(if (chat.isLoading) "Apache está pensando…" else "Organizar mi día", color = Color.Black, fontSize = 14.sp)
                        }
                        planReply?.let {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(it, color = ApacheColors.accentSoft, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (reminders.isNotEmpty()) {
                item {
                    Text(
                        "Recordatorios", color = Color.White, fontSize = 17.sp,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp)
                    )
                }
                items(reminders, key = { "r${it.id}" }) { reminder ->
                    Surface(
                        color = ApacheColors.cardAlt, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = ApacheColors.accentLight, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(reminder.title, color = Color.White, fontSize = 14.sp)
                                Text(reminder.triggerAt.format(DAY_TIME), color = ApacheColors.textMuted, fontSize = 12.sp)
                            }
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
            }

            item { Spacer(modifier = Modifier.height(90.dp)) }
        }

        FloatingActionButton(
            onClick = { creating = true },
            containerColor = ApacheColors.accent,
            contentColor = Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Añadir bloque") }
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

/** Tira horizontal de días (3 atrás, 2 semanas adelante). */
@Composable
private fun DayStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = remember(today) { (-3..14).map { today.plusDays(it.toLong()) } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 2)

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        items(days) { day ->
            val isSelected = day == selected
            val background by animateColorAsState(if (isSelected) ApacheColors.accent else ApacheColors.card, label = "day")
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .then(if (day == today && !isSelected) Modifier.border(1.dp, ApacheColors.accent, RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { onSelect(day) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    day.format(WEEKDAY).take(3).replaceFirstChar { it.titlecase() },
                    color = if (isSelected) Color.Black else ApacheColors.textMuted, fontSize = 12.sp
                )
                Text(day.dayOfMonth.toString(), color = if (isSelected) Color.Black else Color.White, fontSize = 18.sp)
            }
        }
    }
}

/** Línea roja de "ahora" entre los bloques del día de hoy. */
@Composable
private fun NowMarker() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(LocalDateTime.now().format(HOUR), color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF6B6B)))
        Box(modifier = Modifier.weight(1f).height(2.dp).background(Color(0xFFFF6B6B)))
    }
}

/** Bloque de la línea de tiempo: deslizar → completar, ← borrar; tocar = editar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEvent(
    event: CalendarEvent,
    isLast: Boolean,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); true }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val ended = (event.endAt ?: event.startAt.plusHours(1)).isBefore(LocalDateTime.now())
    val accent = when {
        event.status == "completed" -> ApacheColors.completed
        ended -> ApacheColors.overdue
        else -> ApacheColors.accent
    }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // Hora + raíl.
        Column(modifier = Modifier.width(48.dp)) {
            Text(event.startAt.format(HOUR), color = Color.White, fontSize = 13.sp)
            event.endAt?.let { Text(it.format(HOUR), color = ApacheColors.textFaint, fontSize = 11.sp) }
        }
        Column(modifier = Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(accent))
            if (!isLast) Box(modifier = Modifier.width(2.dp).weight(1f).background(Color(0xFF2A3A31)))
        }
        Spacer(modifier = Modifier.width(8.dp))

        SwipeToDismissBox(
            state = state,
            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
            backgroundContent = {
                val toComplete = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        .background(if (toComplete) ApacheColors.completed else Color(0xFF8B2C2C))
                        .padding(horizontal = 18.dp),
                    contentAlignment = if (toComplete) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Icon(if (toComplete) Icons.Filled.Check else Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                }
            }
        ) {
            Surface(
                color = ApacheColors.card,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { onClick() }
            ) {
                Row {
                    Box(modifier = Modifier.width(5.dp).height(64.dp).background(accent))
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            (if (event.status == "completed") "✓ " else "") + event.title,
                            color = Color.White, fontSize = 15.sp
                        )
                        Text(
                            listOfNotNull(
                                event.endAt?.let { "${event.startAt.format(HOUR)} – ${it.format(HOUR)}" },
                                event.location
                            ).joinToString(" · ").ifBlank { "Desliza → hecho · ← borrar" },
                            color = ApacheColors.textMuted, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/** Diálogo para crear o editar un bloque. */
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
        containerColor = ApacheColors.surface,
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
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("Guardar", color = ApacheColors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = ApacheColors.textMuted) } }
    )
}
