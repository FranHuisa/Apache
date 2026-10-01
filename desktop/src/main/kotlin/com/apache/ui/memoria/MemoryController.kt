package com.apache.ui.memoria

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.ConversationMessageDto
import com.apache.model.ConversationSummaryDto
import com.apache.model.MemoryItemDto
import com.apache.model.MemoryRequestDto
import com.apache.network.createMemory
import com.apache.network.deleteMemory
import com.apache.network.fetchConversationMessages
import com.apache.network.fetchConversations
import com.apache.network.fetchMemories
import com.apache.network.updateMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado de la sección Memoria: lo que Apache sabe del usuario (memoria
 * permanente) y el historial de conversaciones.
 */
class MemoryController(private val scope: CoroutineScope) {

    // --- Lo que Apache sabe de ti ---
    var memories by mutableStateOf<List<MemoryItemDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    // Formulario de dato nuevo / edición.
    var formKey by mutableStateOf("")
    var formValue by mutableStateOf("")
    var formType by mutableStateOf("otro")
    var editingId by mutableStateOf<Long?>(null)
        private set

    // --- Conversaciones ---
    var conversations by mutableStateOf<List<ConversationSummaryDto>>(emptyList())
        private set

    var selectedConversation by mutableStateOf<ConversationSummaryDto?>(null)
        private set

    var selectedMessages by mutableStateOf<List<ConversationMessageDto>>(emptyList())
        private set

    /** Datos agrupados por categoría, en el orden en que se muestran. */
    val memoriesByType: List<Pair<String, List<MemoryItemDto>>>
        get() = TYPES.mapNotNull { type ->
            // Cualquier categoría desconocida se muestra dentro de "Otros".
            val items = memories.filter { (if (it.type in TYPES) it.type else "otro") == type }
            if (items.isEmpty()) null else type to items
        }

    fun loadAll() {
        loadMemories()
        loadConversations()
    }

    fun loadMemories() {
        scope.launch {
            isLoading = true
            error = null
            try {
                memories = withContext(Dispatchers.IO) { fetchMemories() }
            } catch (_: Exception) {
                error = "No se ha podido cargar la memoria. Comprueba que el Core está arrancado."
            } finally {
                isLoading = false
            }
        }
    }

    fun loadConversations() {
        scope.launch {
            try {
                conversations = withContext(Dispatchers.IO) { fetchConversations() }
            } catch (_: Exception) {
                error = "No se han podido cargar las conversaciones."
            }
        }
    }

    // --- Formulario ---

    fun beginEdit(item: MemoryItemDto) {
        editingId = item.id
        formKey = item.key
        formValue = item.value
        formType = if (item.type in TYPES) item.type else "otro"
        error = null
    }

    fun resetForm() {
        editingId = null
        formKey = ""
        formValue = ""
        formType = "otro"
    }

    fun save() {
        val key = formKey.trim()
        val value = formValue.trim()

        if (key.isBlank() || value.isBlank()) {
            error = "Escribe qué es (ej.: «comida favorita») y el dato (ej.: «la pizza»)."
            return
        }

        val id = editingId
        val request = MemoryRequestDto(key = key, value = value, type = formType)

        scope.launch {
            isLoading = true
            error = null
            try {
                withContext(Dispatchers.IO) {
                    if (id == null) createMemory(request) else updateMemory(id, request)
                }
                resetForm()
                memories = withContext(Dispatchers.IO) { fetchMemories() }
            } catch (e: Exception) {
                error = e.message ?: "No se ha podido guardar el dato."
            } finally {
                isLoading = false
            }
        }
    }

    fun delete(item: MemoryItemDto) {
        scope.launch {
            isLoading = true
            error = null
            try {
                withContext(Dispatchers.IO) { deleteMemory(item.id) }
                if (editingId == item.id) resetForm()
                memories = memories.filter { it.id != item.id }
            } catch (e: Exception) {
                error = e.message ?: "No se ha podido borrar el dato."
            } finally {
                isLoading = false
            }
        }
    }

    // --- Conversaciones ---

    fun selectConversation(conversation: ConversationSummaryDto) {
        selectedConversation = conversation
        selectedMessages = emptyList()

        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { fetchConversationMessages(conversation.id) }
                if (selectedConversation?.id == conversation.id) selectedMessages = loaded
            } catch (_: Exception) {
                error = "No se han podido cargar los mensajes de esa conversación."
            }
        }
    }

    companion object {
        /** Mismas categorías que UserMemoryService.TYPES en el Core. */
        val TYPES = listOf("personal", "preferencia", "rutina", "trabajo", "salud", "otro")

        fun typeLabel(type: String): String = when (type) {
            "personal" -> "Sobre ti"
            "preferencia" -> "Gustos y preferencias"
            "rutina" -> "Rutinas"
            "trabajo" -> "Trabajo y estudios"
            "salud" -> "Salud"
            else -> "Otros"
        }
    }
}
