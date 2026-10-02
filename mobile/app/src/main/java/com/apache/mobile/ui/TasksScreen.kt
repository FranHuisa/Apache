package com.apache.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.TaskItem
import com.apache.mobile.data.TaskListSummary
import com.apache.mobile.data.TaskStore
import com.apache.mobile.ui.components.ApacheGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listas: compra, tareas y las que cree el usuario (o Apache por voz).
 * Tocar un elemento lo tacha; la X lo quita.
 */
@Composable
fun TasksScreen(chat: ChatViewModel) {
    val app = ApacheApp.get()
    val scope = rememberCoroutineScope()

    var lists by remember { mutableStateOf<List<TaskListSummary>>(emptyList()) }
    var current by remember { mutableStateOf(TaskStore.DEFAULT_LIST) }
    var items by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    var newItem by remember { mutableStateOf("") }
    var newList by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    // Recarga cuando cambia algo aquí o cuando Apache responde en el chat.
    LaunchedEffect(reload, current, chat.messages.size) {
        val (loadedLists, loadedItems) = withContext(Dispatchers.IO) { app.tasks.lists() to app.tasks.items(current) }
        val names = (listOf("compra", TaskStore.DEFAULT_LIST) + loadedLists.map { it.name }).distinct()
        lists = names.map { name -> loadedLists.firstOrNull { it.name == name } ?: TaskListSummary(name, 0, 0) }
        items = loadedItems
    }

    fun change(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            reload++
        }
    }

    fun addCurrent() {
        val text = newItem.trim()
        if (text.isEmpty()) return
        newItem = ""
        change { app.tasks.add(current, listOf(text)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selector de listas.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lists, key = { it.name }) { list ->
                val selected = list.name == current
                val bg by animateColorAsState(if (selected) ApacheColors.accent else ApacheColors.card, label = "list${list.name}")
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
                        .clickable { current = list.name }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        list.name.replaceFirstChar { it.titlecase() },
                        color = if (selected) Color.Black else Color.White, fontSize = 14.sp
                    )
                    if (list.pending > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape)
                                .background(if (selected) Color(0x33000000) else ApacheColors.cardAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${list.pending}", color = if (selected) Color.Black else ApacheColors.accentLight, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(ApacheColors.surface)
                        .clickable { newList = "" }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = ApacheColors.textMuted, modifier = Modifier.size(16.dp))
                    Text(" Nueva", color = ApacheColors.textMuted, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nueva lista: escribir el nombre.
        newList?.let { name ->
            Row(modifier = Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                InputField(
                    value = name,
                    onValueChange = { newList = it },
                    placeholder = "Nombre de la lista (ej.: maleta)",
                    onDone = {
                        val clean = TaskStore.listName(name)
                        if (name.isNotBlank()) current = clean
                        newList = null
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { newList = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = ApacheColors.textMuted)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Añadir a la lista actual.
        Row(modifier = Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            InputField(
                value = newItem,
                onValueChange = { newItem = it },
                placeholder = "Añadir a ${current}…",
                onDone = { addCurrent() },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(ApacheGradient).clickable { addCurrent() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir", tint = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val pending = items.filter { !it.done }
        val done = items.filter { it.done }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Surface(color = ApacheColors.card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Icon(Icons.Filled.PlaylistAddCheck, contentDescription = null, tint = ApacheColors.accent, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("La lista «$current» está vacía", color = Color.White, fontSize = 16.sp)
                            Text(
                                "Escribe arriba o dile a Apache «apunta leche y pan en la compra».",
                                color = ApacheColors.textMuted, fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            items(pending, key = { "p${it.id}" }) { item ->
                TaskRow(item, onToggle = { change { app.tasks.setDone(item.id, true) } }, onDelete = { change { app.tasks.delete(item.id) } })
            }

            if (done.isNotEmpty()) {
                item(key = "doneHeader") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Hecho (${done.size})", color = ApacheColors.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { change { app.tasks.clear(current, onlyDone = true) } }) {
                            Text("Quitar tachados", color = ApacheColors.accentLight, fontSize = 13.sp)
                        }
                    }
                }
                items(done, key = { "d${it.id}" }) { item ->
                    TaskRow(item, onToggle = { change { app.tasks.setDone(item.id, false) } }, onDelete = { change { app.tasks.delete(item.id) } })
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TaskRow(item: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val bg by animateColorAsState(if (item.done) ApacheColors.surface else ApacheColors.cardAlt, label = "task${item.id}")
    val check by animateColorAsState(if (item.done) ApacheColors.accent else Color.Transparent, label = "check${item.id}")

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(onClick = onToggle).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(check)
                .then(if (item.done) Modifier else Modifier.background(Color(0x221DB954))),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.title,
            color = if (item.done) ApacheColors.textFaint else Color.White,
            fontSize = 15.sp,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = ApacheColors.textFaint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = ApacheColors.textFaint) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ApacheColors.card,
            unfocusedContainerColor = ApacheColors.card,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = ApacheColors.accent
        ),
        modifier = modifier
    )
}
