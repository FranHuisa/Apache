package com.apache.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.CalendarEventDto
import com.apache.ui.theme.ApacheColors
import com.apache.ui.theme.calendarTextFieldColors
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay

/** Altura de una hora en la línea de tiempo. */
private val HOUR_HEIGHT = 64.dp

/** Ancho de la columna de horas a la izquierda de la línea de tiempo. */
private val HOUR_LABEL_WIDTH = 56.dp

/** Por debajo de este ancho la pantalla se reorganiza en pestañas (ventana aparte o ventana estrecha). */
private val COMPACT_WIDTH = 860.dp

/**
 * Ventana Horario: organiza un día en bloques de tiempo.
 *
 * A la izquierda, la línea de tiempo del día con los bloques dibujados según
 * su hora; a la derecha, el formulario para añadir/editar bloques y el cuadro
 * para pedirle a Apache que organice el día.
 *
 * @param onOpenWindow si no es null, se muestra un botón para abrir el
 *   horario en una ventana aparte (no se muestra dentro de esa ventana).
 */
@Composable
fun ScheduleScreen(schedule: ScheduleController, onOpenWindow: (() -> Unit)? = null) {

    LaunchedEffect(Unit) { schedule.load() }

    // Hora actual, refrescada cada 30 s para mover la línea de "ahora".
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < COMPACT_WIDTH

        Column(modifier = Modifier.fillMaxSize().padding(if (compact) 16.dp else 28.dp)) {

            ScheduleHeader(schedule, onOpenWindow)

            Spacer(modifier = Modifier.height(16.dp))

            if (compact) {
                CompactLayout(schedule, now)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Timeline(schedule, now, modifier = Modifier.weight(1f).fillMaxHeight())

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        BlockForm(schedule)
                        Spacer(modifier = Modifier.height(16.dp))
                        PlanWithApacheCard(schedule)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cabecera: fecha, navegación y resumen
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleHeader(schedule: ScheduleController, onOpenWindow: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Horario", color = Color.White, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(4.dp))

            val minutes = schedule.plannedMinutes
            val summary = buildString {
                append(schedule.dateLabel)
                if (schedule.isToday) append(" · hoy")
                append(" · ${schedule.dayBlocks.size} bloque(s)")
                if (minutes > 0) append(" · ${minutes / 60} h ${minutes % 60} min planificados")
            }
            Text(text = summary, color = ApacheColors.textCalendarMuted, fontSize = 14.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { schedule.previousDay() }) { Text("◀", color = ApacheColors.accentLight) }
            TextButton(onClick = { schedule.goToToday() }, enabled = !schedule.isToday) {
                Text("Hoy", color = if (schedule.isToday) ApacheColors.textFaint else ApacheColors.accentLight)
            }
            TextButton(onClick = { schedule.nextDay() }) { Text("▶", color = ApacheColors.accentLight) }
            TextButton(onClick = { schedule.load() }, enabled = !schedule.isLoading) {
                Text("Actualizar", color = ApacheColors.accentLight)
            }
            if (onOpenWindow != null) {
                TextButton(onClick = onOpenWindow) { Text("Ventana aparte ↗", color = ApacheColors.accentLight) }
            }
        }
    }

    schedule.error?.let { error ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = error, color = ApacheColors.dangerSoft, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------------------
// Diseño compacto (ventana aparte): pestañas Día / Bloque / Apache
// ---------------------------------------------------------------------------

@Composable
private fun CompactLayout(schedule: ScheduleController, now: LocalTime) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Día", if (schedule.editingEventId == null) "Añadir" else "Editar", "Apache")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { index, label ->
                Surface(
                    color = if (tab == index) ApacheColors.accent else ApacheColors.mutedGreenBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { tab = index }
                ) {
                    Text(
                        text = label,
                        color = if (tab == index) Color.Black else ApacheColors.accentSoft,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (tab) {
            0 -> Timeline(
                schedule,
                now,
                modifier = Modifier.fillMaxSize(),
                onBlockSelected = { tab = 1 },
                onEmptyHourSelected = { tab = 1 }
            )
            1 -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                BlockForm(schedule)
            }
            else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PlanWithApacheCard(schedule)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Línea de tiempo
// ---------------------------------------------------------------------------

/** Bloque ya colocado en la línea de tiempo: su columna dentro de un grupo de bloques solapados. */
private data class PlacedBlock(
    val event: CalendarEventDto,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int
)

/** Bloque con sus minutos de inicio y fin dentro del día (paso previo a colocarlo). */
private class TimelineItem(val event: CalendarEventDto, val start: Int, val end: Int)

/**
 * Reparte los bloques en columnas para que los que se solapan se dibujen uno
 * al lado del otro en vez de encima.
 */
private fun placeBlocks(events: List<CalendarEventDto>): List<PlacedBlock> {
    val items = events.map { event ->
        val start = ScheduleController.blockStart(event).toSecondOfDay() / 60
        val end = (ScheduleController.blockEnd(event).toSecondOfDay() / 60).coerceAtLeast(start + 15)
        TimelineItem(event, start, end)
    }.sortedWith(compareBy({ it.start }, { it.end }))

    val result = mutableListOf<PlacedBlock>()
    var group = mutableListOf<Pair<TimelineItem, Int>>()
    var laneEnds = mutableListOf<Int>()
    var groupEnd = -1

    fun closeGroup() {
        val lanes = laneEnds.size.coerceAtLeast(1)
        group.forEach { (item, lane) -> result.add(PlacedBlock(item.event, item.start, item.end, lane, lanes)) }
        group = mutableListOf()
        laneEnds = mutableListOf()
    }

    items.forEach { item ->
        if (item.start >= groupEnd && group.isNotEmpty()) closeGroup()

        val freeLane = laneEnds.indexOfFirst { it <= item.start }
        val lane = if (freeLane >= 0) freeLane else laneEnds.size
        if (lane == laneEnds.size) laneEnds.add(item.end) else laneEnds[lane] = item.end

        group.add(item to lane)
        groupEnd = maxOf(groupEnd, item.end)
    }
    if (group.isNotEmpty()) closeGroup()

    return result
}

@Composable
private fun Timeline(
    schedule: ScheduleController,
    now: LocalTime,
    modifier: Modifier = Modifier,
    onBlockSelected: () -> Unit = {},
    onEmptyHourSelected: () -> Unit = {}
) {
    val blocks = schedule.dayBlocks

    // Por defecto se muestra de 06:00 a 24:00, ampliando si hay bloques antes.
    val firstHour = minOf(6, blocks.minOfOrNull { ScheduleController.blockStart(it).hour } ?: 6)
    val lastHour = 24
    val hours = lastHour - firstHour

    val scrollState = rememberScrollState()
    val hourHeightPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }

    // Al abrir (o cambiar de día) se desplaza cerca de la hora actual, o del primer bloque.
    LaunchedEffect(schedule.selectedDate, blocks.size) {
        val targetHour = when {
            schedule.isToday -> LocalTime.now().hour - 1
            blocks.isNotEmpty() -> ScheduleController.blockStart(blocks.first()).hour - 1
            else -> 8
        }.coerceIn(firstHour, lastHour - 1)

        scrollState.animateScrollTo(((targetHour - firstHour) * hourHeightPx).toInt())
    }

    Surface(modifier = modifier, color = ApacheColors.surfaceCard, shape = RoundedCornerShape(16.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(vertical = 12.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(HOUR_HEIGHT * hours)) {

                    val blocksWidth = maxWidth - HOUR_LABEL_WIDTH - 12.dp

                    // Rejilla de horas: etiqueta + línea; clic en un hueco = nuevo bloque a esa hora.
                    for (i in 0 until hours) {
                        val hour = firstHour + i
                        Row(
                            modifier = Modifier
                                .offset(y = HOUR_HEIGHT * i)
                                .fillMaxWidth()
                                .height(HOUR_HEIGHT)
                                .clickable {
                                    schedule.beginNewAt(hour)
                                    onEmptyHourSelected()
                                }
                        ) {
                            Text(
                                text = "%02d:00".format(hour),
                                color = ApacheColors.textCalendarMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.width(HOUR_LABEL_WIDTH).padding(start = 12.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(ApacheColors.divider)
                            )
                        }
                    }

                    // Bloques.
                    placeBlocks(blocks).forEach { placed ->
                        val laneWidth = blocksWidth / placed.laneCount
                        val top = HOUR_HEIGHT * ((placed.startMinute - firstHour * 60) / 60f)
                        val blockHeight = HOUR_HEIGHT * ((placed.endMinute - placed.startMinute) / 60f)

                        TimelineBlock(
                            event = placed.event,
                            selected = schedule.editingEventId == placed.event.id,
                            blockHeight = blockHeight,
                            modifier = Modifier
                                .offset(x = HOUR_LABEL_WIDTH + laneWidth * placed.lane, y = top + 8.dp)
                                .width(laneWidth - 4.dp)
                                .height(blockHeight - 2.dp),
                            onClick = {
                                schedule.beginEdit(placed.event)
                                onBlockSelected()
                            }
                        )
                    }

                    // Línea de "ahora".
                    if (schedule.isToday && now.hour >= firstHour) {
                        val nowTop = HOUR_HEIGHT * ((now.toSecondOfDay() / 60 - firstHour * 60) / 60f) + 8.dp
                        Row(
                            modifier = Modifier.offset(x = HOUR_LABEL_WIDTH - 5.dp, y = nowTop - 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(10.dp).background(ApacheColors.dangerSoft, CircleShape))
                            Box(
                                modifier = Modifier
                                    .width(blocksWidth)
                                    .height(2.dp)
                                    .background(ApacheColors.dangerSoft)
                            )
                        }
                    }
                }
            }

            if (schedule.isLoading && blocks.isEmpty()) {
                CircularProgressIndicator(color = ApacheColors.accent, modifier = Modifier.align(Alignment.Center))
            }

            if (!schedule.isLoading && blocks.isEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    color = ApacheColors.surfaceMuted,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Día libre. Haz clic en una hora para añadir un bloque o pídeselo a Apache.",
                        color = ApacheColors.textCalendarSubtle,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineBlock(
    event: CalendarEventDto,
    selected: Boolean,
    blockHeight: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val start = ScheduleController.blockStart(event)
    val end = ScheduleController.blockEnd(event)
    val ended = LocalDateTime.parse(event.startAt).toLocalDate().atTime(end).isBefore(LocalDateTime.now())

    val (background, textColor) = when {
        event.status == "completed" -> ApacheColors.completed to ApacheColors.completedText
        ended -> ApacheColors.overdue to ApacheColors.overdueText
        else -> ApacheColors.confirmed to ApacheColors.accentSoft
    }

    Surface(
        modifier = modifier.clickable { onClick() },
        color = if (selected) background.copy(alpha = 1f) else background.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, ApacheColors.accentLight) else null
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(
                text = (if (event.status == "completed") "✓ " else "") + event.title,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = if (blockHeight < 40.dp) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (blockHeight >= 40.dp) {
                Text(
                    text = "${start.format(ScheduleController.HOUR)} – ${end.format(ScheduleController.HOUR)}",
                    color = textColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formulario de bloque
// ---------------------------------------------------------------------------

@Composable
private fun BlockForm(schedule: ScheduleController) {
    val editing = schedule.dayBlocks.firstOrNull { it.id == schedule.editingEventId }

    Surface(modifier = Modifier.fillMaxWidth(), color = ApacheColors.surfaceCard, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (editing == null) "Nuevo bloque" else "Editar bloque",
                    color = Color.White,
                    fontSize = 18.sp
                )
                if (editing != null) {
                    TextButton(onClick = { schedule.resetForm(); schedule.error = null }) {
                        Text("Cancelar", color = ApacheColors.dangerSoft)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = schedule.title,
                onValueChange = { schedule.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("¿Qué vas a hacer?") },
                singleLine = true,
                colors = calendarTextFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = schedule.startTime,
                    onValueChange = { schedule.startTime = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Desde") },
                    supportingText = { Text("Ej.: 09:00") },
                    singleLine = true,
                    colors = calendarTextFieldColors()
                )
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedTextField(
                    value = schedule.endTime,
                    onValueChange = { schedule.endTime = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Hasta") },
                    supportingText = { Text("Ej.: 10:30") },
                    singleLine = true,
                    colors = calendarTextFieldColors()
                )
            }

            OutlinedTextField(
                value = schedule.note,
                onValueChange = { schedule.note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota (opcional)") },
                singleLine = true,
                colors = calendarTextFieldColors()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { schedule.save() },
                    enabled = !schedule.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
                ) {
                    Text(if (editing == null) "Añadir" else "Guardar")
                }

                if (editing != null && editing.status != "completed") {
                    TextButton(onClick = { schedule.complete(editing) }, enabled = !schedule.isLoading) {
                        Text("✓ Hecho", color = ApacheColors.accentLight)
                    }
                }

                if (editing != null) {
                    TextButton(onClick = { schedule.remove(editing) }, enabled = !schedule.isLoading) {
                        Text("Eliminar", color = ApacheColors.dangerSoft)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Organizar con Apache
// ---------------------------------------------------------------------------

@Composable
private fun PlanWithApacheCard(schedule: ScheduleController) {
    Surface(modifier = Modifier.fillMaxWidth(), color = ApacheColors.surfaceCardAlt, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Organizar con Apache", color = Color.White, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dile qué tienes que hacer y Apache reparte el día en bloques, " +
                    "respetando lo que ya tienes en el horario.",
                color = ApacheColors.textCalendarSubtle,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = schedule.planRequest,
                onValueChange = { schedule.planRequest = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = {
                    Text(
                        "Ej.: me levanto a las 8, quiero estudiar 3 h, ir al gimnasio, " +
                            "hacer la compra y tener la tarde libre a partir de las 19:00",
                        fontSize = 13.sp
                    )
                },
                enabled = !schedule.isPlanning,
                colors = calendarTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { schedule.planWithApache() },
                    enabled = !schedule.isPlanning,
                    colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
                ) {
                    Text("Organizar mi día")
                }

                if (schedule.isPlanning) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(color = ApacheColors.accent, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apache está pensando...", color = ApacheColors.textCalendarSubtle, fontSize = 13.sp)
                }
            }

            schedule.planReply?.let { reply ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = ApacheColors.botBubble, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = reply,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
