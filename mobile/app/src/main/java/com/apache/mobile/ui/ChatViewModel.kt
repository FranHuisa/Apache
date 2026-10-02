package com.apache.mobile.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apache.mobile.ApacheApp
import com.apache.mobile.ai.Attachment
import com.apache.mobile.ai.GeminiException
import com.apache.mobile.data.ChatImage
import com.apache.mobile.data.ChatMessage
import com.apache.mobile.tools.ImageSaver
import com.apache.mobile.voice.Speaker
import com.apache.mobile.voice.SpeechInput
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado y lógica del chat: mensajes, envío al Agent, adjuntos, voz y
 * resumen del día. Compartido por toda la app (Memoria lo usa para retomar
 * conversaciones y Horario para "Organizar mi día").
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val apache = application as ApacheApp
    private val speaker = Speaker(application)

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isListening by mutableStateOf(false)
        private set
    var pendingAttachments by mutableStateOf<List<Attachment>>(emptyList())
        private set
    /** Aviso corto para enseñar en un Snackbar (imagen guardada, errores de voz...). */
    var notice by mutableStateOf<String?>(null)

    private var conversationId: Long? = apache.settings.conversationId
    private var localIds = -1L

    val speechInput = SpeechInput(
        application,
        onResult = { text -> send(text, fromVoice = true) },
        onError = { notice = it },
        onListeningChanged = { isListening = it }
    )

    init {
        loadConversation()
    }

    private fun loadConversation() {
        viewModelScope.launch {
            val id = conversationId
            val loaded = if (id != null) withContext(Dispatchers.IO) { apache.conversations.visibleMessages(id) } else emptyList()
            messages = loaded.ifEmpty { listOf(local("Hola, soy Apache. ¿En qué te ayudo?", isUser = false)) }
        }
    }

    private fun local(text: String, isUser: Boolean, images: List<ChatImage> = emptyList()) =
        ChatMessage(localIds--, isUser, text, images)

    /** Envía un mensaje. Devuelve la respuesta (o null si falla) por si otra pantalla la necesita. */
    fun send(text: String, fromVoice: Boolean = false, onReply: ((String) -> Unit)? = null) {
        val attachments = pendingAttachments
        if ((text.isBlank() && attachments.isEmpty()) || isLoading) return

        if (!apache.settings.isConfigured) {
            notice = "Pon tu API key de Gemini en Ajustes para empezar."
            return
        }

        pendingAttachments = emptyList()
        val shown = if (attachments.isEmpty()) text else "$text\n📎 ${attachments.joinToString { it.name }}".trim()
        messages = messages + local(shown.ifBlank { "📎 ${attachments.joinToString { it.name }}" }, isUser = true)

        runAgent(text, attachments, fromVoice, hideUserMessage = false, onReply = onReply)
    }

    private fun runAgent(
        text: String,
        attachments: List<Attachment>,
        fromVoice: Boolean,
        hideUserMessage: Boolean,
        onReply: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            isLoading = true
            try {
                val reply = apache.agent.send(conversationId, text, attachments, fromVoice, hideUserMessage)
                conversationId = reply.conversationId
                apache.settings.conversationId = reply.conversationId
                messages = messages + local(reply.text, isUser = false, images = reply.images)

                if (fromVoice && apache.settings.speakReplies) speaker.speak(reply.text)
                onReply?.invoke(reply.text)
            } catch (e: GeminiException) {
                messages = messages + local(e.message ?: "Algo ha fallado.", isUser = false)
            } catch (e: Exception) {
                messages = messages + local("Algo ha fallado: ${e.message ?: e.javaClass.simpleName}", isUser = false)
            } finally {
                isLoading = false
            }
        }
    }

    /** Resumen del día la primera vez que se abre la app cada día. */
    fun dailySummaryIfNeeded() {
        val today = LocalDate.now().toString()
        if (!apache.settings.isConfigured || apache.settings.lastSummaryDate == today || isLoading) return
        apache.settings.lastSummaryDate = today

        runAgent(
            "(Mensaje automático al abrir Apache por primera vez hoy, no lo menciones.) Dame mi resumen " +
                "de hoy: salúdame por mi nombre si lo sabes, qué tengo hoy en el calendario, mis " +
                "recordatorios pendientes y el tiempo si sabes mi ciudad. Muy breve: 2-4 líneas.",
            emptyList(), fromVoice = false, hideUserMessage = true
        )
    }

    fun newConversation() {
        if (isLoading) return
        conversationId = null
        apache.settings.conversationId = null
        messages = listOf(local("Nueva conversación. ¿En qué te ayudo?", isUser = false))
    }

    fun openConversation(id: Long) {
        if (isLoading) return
        conversationId = id
        apache.settings.conversationId = id
        loadConversation()
    }

    // --- Adjuntos ---

    fun addAttachment(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val attachment = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "archivo"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                    if (bytes.size > MAX_ATTACHMENT) return@runCatching null
                    if (!(mime.startsWith("image/") || mime == "application/pdf" || mime.startsWith("text/"))) return@runCatching null
                    Attachment(name, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
                }.getOrNull()
            }

            if (attachment == null) {
                notice = "Solo se pueden adjuntar imágenes, PDF o texto de hasta 10 MB."
            } else {
                pendingAttachments = (pendingAttachments + attachment).take(5)
            }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        pendingAttachments = pendingAttachments - attachment
    }

    // --- Imágenes ---

    fun saveImage(image: ChatImage) {
        viewModelScope.launch {
            notice = try {
                "Guardada en " + withContext(Dispatchers.IO) { ImageSaver.save(getApplication(), image) }
            } catch (e: Exception) {
                "No se ha podido guardar la imagen."
            }
        }
    }

    // --- Voz ---

    fun toggleListening() {
        if (isListening) speechInput.stop() else {
            speaker.stop()
            speechInput.start()
        }
    }

    override fun onCleared() {
        speechInput.stop()
        speaker.shutdown()
    }

    private companion object {
        const val MAX_ATTACHMENT = 10 * 1024 * 1024
    }
}
