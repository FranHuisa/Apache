package com.apache.ui.memoria

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.ConversationSummaryDto
import com.apache.model.MemoryItemDto
import com.apache.ui.chat.ChatController
import com.apache.ui.theme.ApacheColors
import com.apache.ui.theme.calendarTextFieldColors

/**
 * Sección Memoria.
 *
 * - «Lo que sabe de ti»: datos permanentes que Apache tiene en cuenta en todas
 *   las conversaciones. Se pueden añadir, editar y borrar; Apache también los
 *   guarda solo cuando le cuentas algo («recuerda que...»).
 * - «Conversaciones»: historial de conversaciones pasadas, con la opción de
 *   retomar cualquiera en el chat.
 *
 * @param onOpenChat se llama tras retomar o empezar una conversación, para
 *   cambiar a la sección Chat.
 */
@Composable
fun MemoriaScreen(memory: MemoryController, chat: ChatController, onOpenChat: () -> Unit) {

    LaunchedEffect(Unit) { memory.loadAll() }

    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Memoria", color = Color.White, fontSize = 26.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${memory.memories.size} dato(s) guardado(s) · ${memory.conversations.size} conversación(es)",
                    color = ApacheColors.textCalendarMuted,
                    fontSize = 14.sp
                )
            }
            TextButton(onClick = { memory.loadAll() }, enabled = !memory.isLoading) {
                Text("Actualizar", color = ApacheColors.accentLight)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Lo que sabe de ti", "Conversaciones").forEachIndexed { index, label ->
                Surface(
                    color = if (tab == index) ApacheColors.accent else ApacheColors.mutedGreenBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { tab = index }
                ) {
                    Text(
                        text = label,
                        color = if (tab == index) Color.Black else ApacheColors.accentSoft,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        memory.error?.let { error ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = error, color = ApacheColors.dangerSoft, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (tab) {
            0 -> FactsTab(memory)
            else -> ConversationsTab(memory, chat, onOpenChat)
        }
    }
}

// ---------------------------------------------------------------------------
// Lo que sabe de ti
// ---------------------------------------------------------------------------

@Composable
private fun FactsTab(memory: MemoryController) {
    Row(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
        ) {
            if (memory.memories.isEmpty() && !memory.isLoading) {
                Surface(color = ApacheColors.surfaceMuted, shape = RoundedCornerShape(14.dp)) {
                    Text(
                        text = "Apache todavía no recuerda nada de ti. Cuéntaselo en el chat " +
                            "(«recuerda que me llamo Fran», «mi comida favorita es la pizza») " +
                            "o añádelo aquí.",
                        color = ApacheColors.textCalendarSubtle,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }

            memory.memoriesByType.forEach { (type, items) ->
                Text(
                    text = MemoryController.typeLabel(type),
                    color = ApacheColors.accent,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                items.forEach { item -> MemoryCard(memory, item) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            MemoryForm(memory)
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryController, item: MemoryItemDto) {
    val selected = memory.editingId == item.id

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        color = if (selected) ApacheColors.confirmed else ApacheColors.surfaceCardAlt,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.key, color = ApacheColors.textCalendarMuted, fontSize = 12.sp)
                Text(text = item.value, color = Color.White, fontSize = 15.sp)
            }
            TextButton(onClick = { memory.beginEdit(item) }) {
                Text("Editar", color = ApacheColors.accentLight, fontSize = 13.sp)
            }
            TextButton(onClick = { memory.delete(item) }, enabled = !memory.isLoading) {
                Text("Olvidar", color = ApacheColors.dangerSoft, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MemoryForm(memory: MemoryController) {
    Surface(modifier = Modifier.fillMaxWidth(), color = ApacheColors.surfaceCard, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (memory.editingId == null) "Añadir dato" else "Editar dato",
                    color = Color.White,
                    fontSize = 18.sp
                )
                if (memory.editingId != null) {
                    TextButton(onClick = { memory.resetForm(); memory.error = null }) {
                        Text("Cancelar", color = ApacheColors.dangerSoft)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = memory.formKey,
                onValueChange = { memory.formKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Qué es") },
                placeholder = { Text("Ej.: comida favorita") },
                singleLine = true,
                colors = calendarTextFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = memory.formValue,
                onValueChange = { memory.formValue = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dato") },
                placeholder = { Text("Ej.: la pizza") },
                colors = calendarTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "Categoría", color = ApacheColors.textCalendarSubtle, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            // Selector de categoría: dos filas de "chips".
            MemoryController.TYPES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { type ->
                        val selected = memory.formType == type
                        Surface(
                            color = if (selected) ApacheColors.accent else ApacheColors.mutedGreenBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { memory.formType = type }
                        ) {
                            Text(
                                text = MemoryController.typeLabel(type),
                                color = if (selected) Color.Black else ApacheColors.accentSoft,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { memory.save() },
                enabled = !memory.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
            ) {
                Text(if (memory.editingId == null) "Guardar en memoria" else "Guardar cambios")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Conversaciones
// ---------------------------------------------------------------------------

@Composable
private fun ConversationsTab(memory: MemoryController, chat: ChatController, onOpenChat: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {

            Button(
                onClick = {
                    chat.startNewConversation()
                    onOpenChat()
                },
                enabled = !chat.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
            ) {
                Text("+ Nueva conversación")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (memory.conversations.isEmpty()) {
                Text("Todavía no hay conversaciones guardadas.", color = ApacheColors.textCalendarSubtle, fontSize = 14.sp)
            }

            memory.conversations.forEach { conversation ->
                ConversationRow(
                    conversation = conversation,
                    selected = memory.selectedConversation?.id == conversation.id,
                    current = chat.conversationId == conversation.id,
                    onClick = { memory.selectConversation(conversation) }
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = ApacheColors.surfaceCard,
            shape = RoundedCornerShape(16.dp)
        ) {
            val selected = memory.selectedConversation

            if (selected == null) {
                Text(
                    text = "Elige una conversación para ver sus mensajes.",
                    color = ApacheColors.textCalendarSubtle,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selected.title,
                            color = Color.White,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                chat.openConversation(selected.id, memory.selectedMessages)
                                onOpenChat()
                            },
                            enabled = !chat.isLoading && memory.selectedMessages.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ApacheColors.accent,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Retomar en el chat")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        memory.selectedMessages.forEach { message ->
                            val isUser = message.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (isUser) ApacheColors.accent else ApacheColors.botBubble,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = message.text,
                                        color = if (isUser) Color.Black else Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationSummaryDto,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() },
        color = if (selected) ApacheColors.confirmed else ApacheColors.surfaceCardAlt,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = conversation.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.updatedAt.replace('T', ' ').take(16) +
                    " · ${conversation.messageCount} mensaje(s)" +
                    if (current) " · actual" else "",
                color = ApacheColors.textCalendarMuted,
                fontSize = 12.sp
            )
        }
    }
}
