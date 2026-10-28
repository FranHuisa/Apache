package com.apache.mobile.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.GymExercise
import com.apache.mobile.data.GymLog
import com.apache.mobile.data.GymRoutine
import com.apache.mobile.data.GymStore
import com.apache.mobile.ui.components.ApacheGradient
import com.apache.mobile.ui.components.SuggestionPill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-ES"))

/**
 * Gimnasio: tus rutinas con sus ejercicios (series × repeticiones y peso),
 * lo que toca hoy y lo que has entrenado. El peso se cambia con − / +, y
 * tocando un ejercicio se edita entero.
 */
@Composable
fun GymScreen(chat: ChatViewModel, onOpenChat: () -> Unit) {
    val app = ApacheApp.get()
    val scope = rememberCoroutineScope()

    var routines by remember { mutableStateOf<List<GymRoutine>>(emptyList()) }
    var today by remember { mutableStateOf<GymRoutine?>(null) }
    var recent by remember { mutableStateOf<List<Pair<LocalDate, List<GymLog>>>>(emptyList()) }
    var monthDays by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var editing by remember { mutableStateOf<GymExercise?>(null) }
    var addingTo by remember { mutableStateOf<GymRoutine?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload, chat.messages.size) {
        val data = withContext(Dispatchers.IO) {
            GymData(
                routines = app.gym.routines(),
                today = app.gym.routineForToday(),
                recent = app.gym.recentLogs(60).groupBy { it.day }.toList().take(5),
                monthDays = app.gym.trainedDays(LocalDate.now().withDayOfMonth(1)).size
            )
        }
        routines = data.routines
        today = data.today
        recent = data.recent
        monthDays = data.monthDays
        if (expanded.isEmpty()) data.today?.let { expanded = setOf(it.id) }
    }

    fun change(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            reload++
        }
    }

    val trainedToday = recent.firstOrNull()?.first == LocalDate.now()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Lo de hoy.
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ApacheGradient)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when {
                                    trainedToday -> "Hoy ya has entrenado 💪"
                                    today != null -> "Hoy toca: ${today!!.name}"
                                    else -> "Gimnasio"
                                },
                                color = Color.Black, fontSize = 16.sp
                            )
                            Text("$monthDays días entrenados este mes", color = Color(0xCC000000), fontSize = 13.sp)
                        }
                    }
                }
            }

            if (routines.isEmpty()) {
                item {
                    Surface(color = ApacheColors.card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Aún no tienes rutinas", color = Color.White, fontSize = 15.sp)
                            Text(
                                "Crea una con + o pídesela a Apache: te la diseña y la guarda.",
                                color = ApacheColors.textMuted, fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SuggestionPill("Hazme una rutina de 3 días para ganar fuerza") {
                                chat.send("Hazme una rutina de gimnasio de 3 días para ganar fuerza y guárdala")
                                onOpenChat()
                            }
                        }
                    }
                }
            }

            items(routines, key = { "r${it.id}" }) { routine ->
                RoutineCard(
                    routine = routine,
                    open = routine.id in expanded,
                    isToday = routine.id == today?.id,
                    onToggle = { expanded = if (routine.id in expanded) expanded - routine.id else expanded + routine.id },
                    onWeight = { exercise, delta ->
                        change { app.gym.updateExercise(exercise.id, weight = ((exercise.weight ?: 0.0) + delta).coerceAtLeast(0.0)) }
                    },
                    onEdit = { editing = it },
                    onAdd = { addingTo = routine },
                    onDone = { change { app.gym.logRoutine(routine) }; chat.notice = "¡Bien! «${routine.name}» apuntada 💪" },
                    onDelete = { change { app.gym.deleteRoutine(routine.id) } }
                )
            }

            // Últimos entrenamientos.
            if (recent.isNotEmpty()) {
                item {
                    Text("Últimos entrenamientos", color = ApacheColors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                items(recent, key = { "d${it.first}" }) { (day, logs) ->
                    Surface(color = ApacheColors.cardAlt, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            val name = logs.firstNotNullOfOrNull { it.routineName }
                            Text(
                                day.format(LOG_DAY).replaceFirstChar { it.titlecase() } + (name?.let { " · $it" } ?: ""),
                                color = Color.White, fontSize = 14.sp
                            )
                            Text(
                                logs.joinToString(" · ") { log -> log.exercise + (log.weight?.let { " ${GymStore.formatWeight(it)}" } ?: "") },
                                color = ApacheColors.textMuted, fontSize = 12.sp, maxLines = 2
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { creating = true },
            containerColor = ApacheColors.accent,
            contentColor = Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Nueva rutina") }
    }

    editing?.let { exercise ->
        ExerciseDialog(
            title = "Editar ejercicio",
            initial = exercise,
            onDismiss = { editing = null },
            onDelete = { change { app.gym.deleteExercise(exercise.id) }; editing = null },
            onSave = { input ->
                change {
                    app.gym.deleteExercise(exercise.id)
                    app.gym.addExercise(exercise.routineId, input)
                }
                editing = null
            }
        )
    }

    addingTo?.let { routine ->
        ExerciseDialog(
            title = "Añadir a ${routine.name}",
            initial = null,
            onDismiss = { addingTo = null },
            onDelete = null,
            onSave = { input -> change { app.gym.addExercise(routine.id, input) }; addingTo = null }
        )
    }

    if (creating) {
        NewRoutineDialog(
            onDismiss = { creating = false },
            onSave = { name, day, exercises ->
                change { app.gym.saveRoutine(name, day, exercises) }
                creating = false
            }
        )
    }
}

@Composable
private fun RoutineCard(
    routine: GymRoutine,
    open: Boolean,
    isToday: Boolean,
    onToggle: () -> Unit,
    onWeight: (GymExercise, Double) -> Unit,
    onEdit: (GymExercise) -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "gym${routine.id}")

    Surface(color = ApacheColors.card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.name, color = Color.White, fontSize = 16.sp)
                        if (isToday) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "HOY", color = Color.Black, fontSize = 10.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(ApacheColors.accent).padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        listOfNotNull(routine.day?.replaceFirstChar { it.titlecase() }, "${routine.exercises.size} ejercicios").joinToString(" · "),
                        color = ApacheColors.textMuted, fontSize = 13.sp
                    )
                }
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = ApacheColors.textMuted, modifier = Modifier.rotate(rotation))
            }

            if (open) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    routine.exercises.forEach { exercise ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp)).background(ApacheColors.cardAlt)
                                .clickable { onEdit(exercise) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.name, color = Color.White, fontSize = 15.sp)
                                Text(
                                    "${exercise.sets} × ${exercise.reps}" + (exercise.restSeconds?.let { " · ${it}s" } ?: "") +
                                        (exercise.notes?.let { " · $it" } ?: ""),
                                    color = ApacheColors.accentSoft, fontSize = 12.sp
                                )
                            }
                            // Peso con − / + (2,5 kg).
                            IconButton(onClick = { onWeight(exercise, -2.5) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Remove, contentDescription = "Menos peso", tint = ApacheColors.textMuted, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                if (exercise.weight == null) "—" else GymStore.formatWeight(exercise.weight),
                                color = Color.White, fontSize = 14.sp, modifier = Modifier.width(58.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            IconButton(onClick = { onWeight(exercise, 2.5) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Más peso", tint = ApacheColors.accentLight, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(ApacheGradient).clickable(onClick = onDone)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Text(" Hecha hoy", color = Color.Black, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(onClick = onAdd) { Text("+ Ejercicio", color = ApacheColors.accentLight, fontSize = 13.sp) }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Borrar rutina", tint = ApacheColors.textFaint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseDialog(
    title: String,
    initial: GymExercise?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (GymStore.ExerciseInput) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var sets by remember { mutableStateOf(initial?.sets?.toString() ?: "3") }
    var reps by remember { mutableStateOf(initial?.reps ?: "10") }
    var weight by remember { mutableStateOf(initial?.weight?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()) }
    var rest by remember { mutableStateOf(initial?.restSeconds?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApacheColors.surface,
        title = { Text(title, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ejercicio") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sets, onValueChange = { sets = it.filter(Char::isDigit) }, label = { Text("Series") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Peso (kg)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rest, onValueChange = { rest = it.filter(Char::isDigit) }, label = { Text("Descanso (s)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Quitar ejercicio", color = ApacheColors.danger) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onSave(
                    GymStore.ExerciseInput(
                        name = name.trim(),
                        sets = sets.toIntOrNull() ?: 3,
                        reps = reps.ifBlank { "10" },
                        weight = weight.replace(',', '.').toDoubleOrNull(),
                        restSeconds = rest.toIntOrNull()
                    )
                )
            }) { Text("Guardar", color = ApacheColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = ApacheColors.textMuted) } }
    )
}

/**
 * Nueva rutina: nombre, día y los ejercicios escritos uno por línea, tal cual:
 * "Press banca 4x8 60", "Sentadilla 5x5 80kg", "Dominadas 3xal fallo".
 */
@Composable
private fun NewRoutineDialog(onDismiss: () -> Unit, onSave: (String, String?, List<GymStore.ExerciseInput>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf("") }
    val parsed = GymStore.parseLines(lines)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApacheColors.surface,
        title = { Text("Nueva rutina", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre (ej.: Pierna)") }, singleLine = true)
                OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Día (opcional, ej.: lunes)") }, singleLine = true)
                OutlinedTextField(
                    value = lines,
                    onValueChange = { lines = it },
                    label = { Text("Ejercicios, uno por línea") },
                    placeholder = { Text("Sentadilla 4x8 80\nPrensa 3x12 120\nGemelos 4x15") },
                    modifier = Modifier.heightIn(min = 120.dp)
                )
                if (parsed.isNotEmpty()) {
                    Text("${parsed.size} ejercicios: " + parsed.joinToString { "${it.name} ${it.sets}×${it.reps}" }, color = ApacheColors.accentSoft, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || parsed.isEmpty()) return@TextButton
                onSave(name.trim(), day.trim().ifBlank { null }, parsed)
            }) { Text("Crear", color = ApacheColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = ApacheColors.textMuted) } }
    )
}

private data class GymData(
    val routines: List<GymRoutine>,
    val today: GymRoutine?,
    val recent: List<Pair<LocalDate, List<GymLog>>>,
    val monthDays: Int
)
