package com.apache.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import com.apache.mobile.ui.components.ApacheGradient
import com.apache.mobile.ui.components.SuggestionPill
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.ConversationSummary
import com.apache.mobile.data.DiaryEntry
import com.apache.mobile.data.MemoryFact
import com.apache.mobile.data.Routine
import com.apache.mobile.data.MemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Memoria: lo que Apache sabe de ti, en tarjetas por categoría que se
 * despliegan al tocarlas, y las conversaciones pasadas.
 */
@Composable
fun MemoryScreen(chat: ChatViewModel, onOpenChat: () -> Unit) {
    val app = ApacheApp.get()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var facts by remember { mutableStateOf<List<MemoryFact>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf("personal")) }

    var diary by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
    var routines by remember { mutableStateOf<List<Routine>>(emptyList()) }

    LaunchedEffect(reloadKey, chat.messages.size) {
        val (loadedFacts, loadedConversations) = withContext(Dispatchers.IO) { app.memory.all() to app.conversations.list() }
        facts = loadedFacts
        conversations = loadedConversations
        val (loadedDiary, loadedRoutines) = withContext(Dispatchers.IO) { app.diary.recent(90) to app.routines.all() }
        diary = loadedDiary
        routines = loadedRoutines
    }

    val visibleFacts = if (query.isBlank()) facts else facts.filter {
        it.key.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = ApacheColors.accent, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Memoria", color = Color.White, fontSize = 24.sp)
                    Text(
                        "${facts.size} datos · ${diary.size} días en el diario · ${routines.size} rutinas",
                        color = ApacheColors.textMuted, fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de pestaña tipo "pastilla".
            Row(
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(50)).background(ApacheColors.surface).padding(4.dp)
            ) {
                listOf("Sobre ti", "Diario", "Rutinas", "Charlas").forEachIndexed { index, label ->
                    val selected = tab == index
                    val bg by animateColorAsState(if (selected) ApacheColors.accent else Color.Transparent, label = "tab$index")
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg)
                            .clickable { tab = index }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) Color.Black else ApacheColors.textMuted, fontSize = 13.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                if (tab == 0) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar en tu memoria") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (facts.isEmpty()) {
                        item {
                            Surface(color = ApacheColors.card, shape = RoundedCornerShape(20.dp)) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text("Apache todavía no recuerda nada de ti", color = Color.White, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Díselo en el chat («recuerda que vivo en Madrid») o pulsa + para añadirlo tú.",
                                        color = ApacheColors.textMuted, fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SuggestionPill("Recuerda que me llamo…") {
                                        onOpenChat()
                                    }
                                }
                            }
                        }
                    }

                    MemoryStore.TYPES.forEach { type ->
                        val group = visibleFacts.filter { (if (it.type in MemoryStore.TYPES) it.type else "otro") == type }
                        if (group.isNotEmpty()) {
                            item(key = "cat$type") {
                                CategoryCard(
                                    type = type,
                                    facts = group,
                                    open = query.isNotBlank() || type in expanded,
                                    onToggle = { expanded = if (type in expanded) expanded - type else expanded + type },
                                    onEdit = { editing = it }
                                )
                            }
                        }
                    }
                } else if (tab == 1) {
                    diaryItems(diary, onAsk = { chat.askAboutDay(); onOpenChat() }, onDelete = { entry ->
                        scope.launch {
                            withContext(Dispatchers.IO) { app.diary.delete(entry.day) }
                            reloadKey++
                        }
                    })
                } else if (tab == 2) {
                    routineItems(routines, onDelete = { routine ->
                        scope.launch {
                            withContext(Dispatchers.IO) { app.routines.delete(routine.id) }
                            reloadKey++
                        }
                    }, onRun = { routine ->
                        chat.send(routine.trigger)
                        onOpenChat()
                    })
                } else {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ApacheGradient)
                                .clickable { chat.newConversation(); onOpenChat() }
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AddComment, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Nueva conversación", color = Color.Black, fontSize = 15.sp)
                            }
                        }
                    }

                    if (conversations.isEmpty()) {
                        item { Text("Aún no hay conversaciones guardadas.", color = ApacheColors.textMuted, fontSize = 14.sp) }
                    }

                    items(conversations, key = { "c${it.id}" }) { conversation ->
                        Surface(
                            color = ApacheColors.cardAlt,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                chat.openConversation(conversation.id)
                                onOpenChat()
                            }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = ApacheColors.accent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conversation.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(conversation.updatedAt.replace('T', ' ').take(16), color = ApacheColors.textFaint, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { app.conversations.delete(conversation.id) }
                                        if (app.settings.conversationId == conversation.id) chat.newConversation()
                                        reloadKey++
                                    }
                                }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Borrar", tint = ApacheColors.danger)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(90.dp)) }
            }
        }

        if (tab == 0) {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = ApacheColors.accent,
                contentColor = Color.Black,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir dato")
            }
        }
    }

    if (creating || editing != null) {
        FactDialog(
            fact = editing,
            onDismiss = { creating = false; editing = null },
            onChanged = { creating = false; editing = null; reloadKey++ }
        )
    }
}

private fun typeIcon(type: String): ImageVector = when (type) {
    "personal" -> Icons.Filled.Person
    "preferencia" -> Icons.Filled.Favorite
    "rutina" -> Icons.Filled.Repeat
    "trabajo" -> Icons.Filled.Work
    "salud" -> Icons.Filled.FavoriteBorder
    else -> Icons.Filled.Lightbulb
}

/** Tarjeta de una categoría: cabecera con icono y número; al tocarla se despliega. */
@Composable
private fun CategoryCard(
    type: String,
    facts: List<MemoryFact>,
    open: Boolean,
    onToggle: () -> Unit,
    onEdit: (MemoryFact) -> Unit
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron$type")

    Surface(
        color = ApacheColors.card,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(ApacheGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon(type), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(MemoryStore.typeLabel(type), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Surface(color = ApacheColors.cardAlt, shape = RoundedCornerShape(50)) {
                    Text(
                        "${facts.size}", color = ApacheColors.accentLight, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore, contentDescription = null, tint = ApacheColors.textMuted,
                    modifier = Modifier.rotate(rotation)
                )
            }

            if (open) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    facts.forEach { fact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp)).background(ApacheColors.cardAlt)
                                .clickable { onEdit(fact) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fact.key, color = ApacheColors.accentSoft, fontSize = 12.sp)
                                Text(fact.value, color = Color.White, fontSize = 15.sp)
                            }
                            Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = ApacheColors.textFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactDialog(fact: MemoryFact?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val app = ApacheApp.get()
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(fact?.key.orEmpty()) }
    var value by remember { mutableStateOf(fact?.value.orEmpty()) }
    var type by remember { mutableStateOf(fact?.type ?: "otro") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fact == null) "Añadir dato" else "Editar dato") },
        text = {
            Column {
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Qué es (ej.: ciudad)") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Dato (ej.: Madrid)") })
                Spacer(modifier = Modifier.height(8.dp))
                MemoryStore.TYPES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                label = { Text(MemoryStore.typeLabel(option), fontSize = 11.sp) }
                            )
                        }
                    }
                }
                if (fact != null) {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { app.memory.delete(fact.id) }
                            onChanged()
                        }
                    }) { Text("Olvidar este dato", color = ApacheColors.danger) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (key.isBlank() || value.isBlank()) return@TextButton
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (fact == null) app.memory.remember(key, value, type) else app.memory.update(fact.id, key, value, type)
                        }
                        onChanged()
                    }
                }
            ) { Text("Guardar", color = ApacheColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = ApacheColors.textMuted) } }
    )
}

private val DIARY_DAY = java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale.forLanguageTag("es-ES"))

private fun moodEmoji(mood: String?): String {
    val m = mood?.lowercase().orEmpty()
    return when {
        m.isEmpty() -> "📓"
        listOf("feliz", "content", "genial", "bien", "alegre", "motivad").any { m.contains(it) } -> "😊"
        listOf("cansad", "agotad").any { m.contains(it) } -> "😴"
        listOf("triste", "mal", "baj").any { m.contains(it) } -> "😔"
        listOf("estres", "agobi", "nervios", "ansios").any { m.contains(it) } -> "😣"
        listOf("enfad", "molest").any { m.contains(it) } -> "😠"
        else -> "🙂"
    }
}

/** Pestaña Diario: un día por tarjeta, con el ánimo. */
private fun LazyListScope.diaryItems(entries: List<DiaryEntry>, onAsk: () -> Unit, onDelete: (DiaryEntry) -> Unit) {
    item {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ApacheGradient).clickable(onClick = onAsk)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📓", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Contarle a Apache qué tal el día", color = Color.Black, fontSize = 15.sp)
                    Text("Luego pregúntale «¿qué hice el finde?»", color = Color(0xCC000000), fontSize = 12.sp)
                }
            }
        }
    }
    if (entries.isEmpty()) {
        item { Text("Tu diario está vacío. Cada noche Apache te preguntará qué tal el día.", color = ApacheColors.textMuted, fontSize = 14.sp) }
    }
    items(entries, key = { "diary${it.day}" }) { entry ->
        Surface(color = ApacheColors.card, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(moodEmoji(entry.mood), fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.day.format(DIARY_DAY).replaceFirstChar { it.titlecase() }, color = Color.White, fontSize = 15.sp)
                        entry.mood?.let { Text(it, color = ApacheColors.accentSoft, fontSize = 12.sp) }
                    }
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Borrar", tint = ApacheColors.textFaint)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(entry.text, color = ApacheColors.textMuted, fontSize = 14.sp)
            }
        }
    }
}

/** Pestaña Rutinas: frase y pasos; se pueden lanzar o borrar. */
private fun LazyListScope.routineItems(routines: List<Routine>, onDelete: (Routine) -> Unit, onRun: (Routine) -> Unit) {
    item {
        Surface(color = ApacheColors.card, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Crea rutinas hablando", color = Color.White, fontSize = 15.sp)
                Text(
                    "Dile a Apache: «cuando diga me voy a dormir, pon una alarma a las 7, dime qué tengo mañana y cuánto voy a dormir». " +
                        "Luego basta con decir la frase.",
                    color = ApacheColors.textMuted, fontSize = 13.sp
                )
            }
        }
    }
    items(routines, key = { "routine${it.id}" }) { routine ->
        Surface(color = ApacheColors.cardAlt, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("«${routine.trigger}»", color = ApacheColors.accentLight, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRun(routine) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Ejecutar", tint = ApacheColors.accent)
                    }
                    IconButton(onClick = { onDelete(routine) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Borrar", tint = ApacheColors.textFaint)
                    }
                }
                routine.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
