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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.apache.mobile.data.MemoryFact
import com.apache.mobile.data.MemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Memoria: lo que Apache sabe de ti (editable) y las conversaciones pasadas. */
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

    LaunchedEffect(reloadKey) {
        val (loadedFacts, loadedConversations) = withContext(Dispatchers.IO) { app.memory.all() to app.conversations.list() }
        facts = loadedFacts
        conversations = loadedConversations
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Memoria", color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))

        TabRow(selectedTabIndex = tab, containerColor = ApacheColors.background, contentColor = ApacheColors.accent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Lo que sabe de ti") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Conversaciones") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (tab == 0) {
                item {
                    Button(
                        onClick = { creating = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
                    ) { Text("+ Añadir dato") }
                }

                if (facts.isEmpty()) {
                    item {
                        Text(
                            "Apache todavía no recuerda nada de ti. Díselo en el chat («recuerda que vivo en Madrid») o añádelo aquí.",
                            color = ApacheColors.textMuted, fontSize = 14.sp
                        )
                    }
                }

                MemoryStore.TYPES.forEach { type ->
                    val group = facts.filter { (if (it.type in MemoryStore.TYPES) it.type else "otro") == type }
                    if (group.isNotEmpty()) {
                        item(key = "h$type") {
                            Text(MemoryStore.typeLabel(type), color = ApacheColors.accent, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        items(group, key = { "f${it.id}" }) { fact ->
                            Surface(
                                color = ApacheColors.cardAlt,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().clickable { editing = fact }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(fact.key, color = ApacheColors.textMuted, fontSize = 12.sp)
                                    Text(fact.value, color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Button(
                        onClick = { chat.newConversation(); onOpenChat() },
                        colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
                    ) { Text("+ Nueva conversación") }
                }

                items(conversations, key = { "c${it.id}" }) { conversation ->
                    Surface(
                        color = ApacheColors.cardAlt,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            chat.openConversation(conversation.id)
                            onOpenChat()
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conversation.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(conversation.updatedAt.replace('T', ' ').take(16), color = ApacheColors.textFaint, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { app.conversations.delete(conversation.id) }
                                    if (app.settings.conversationId == conversation.id) chat.newConversation()
                                    reloadKey++
                                }
                            }) { Text("Borrar", color = ApacheColors.danger, fontSize = 13.sp) }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
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
