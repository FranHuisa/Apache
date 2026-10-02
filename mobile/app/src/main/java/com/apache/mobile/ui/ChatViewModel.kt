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

    /** Texto para la caja de escribir (lo rellena "Compartir con Apache"). */
    var draft by mutableStateOf("")

    /** Sube cuando se concede el permiso de ubicación (Inicio vuelve a mirar el tiempo). */
    var locationVersion by mutableStateOf(0)

    /** Sube cada vez que llega algo compartido desde otra app (para abrir el chat). */
    var shareCount by mutableStateOf(0)

    private var conversationId: Long? = apache.settings.conversationId
    private var localIds = -1L

    // --- Modo conversación (manos libres) ---

    /** true mientras dura la conversación manos libres. */
    var conversationMode by mutableStateOf(false)
        private set

    /** Lo último que se oyó y lo último que respondió Apache (para la pantalla de conversación). */
    var lastHeard by mutableStateOf("")
        private set
    var lastAnswer by mutableStateOf("")
        private set

    /** true mientras Apache habla en el modo conversación. */
    var isSpeaking by mutableStateOf(false)
        private set

    /** El siguiente mensaje responde a "¿qué tal el día?" (aviso del diario). */
    private var diaryPending = false

    /** Silencios seguidos: con dos, se acaba la conversación sola. */
    private var silences = 0

    val speechInput: SpeechInput = SpeechInput(
        application,
        onResult = { text -> onHeard(text) },
        onError = { message ->
            if (conversationMode) speakThenListen(message) else notice = message
        },
        onListeningChanged = { isListening = it },
        onNothingHeard = {
            if (!conversationMode) {
                notice = "No te he entendido."
            } else if (++silences >= 2) {
                endConversation(say = "Te dejo, si necesitas algo me llamas.")
            } else {
                listenAgain()
            }
        }
    )

    /** Vuelve a escuchar (función aparte: speechInput no puede usarse a sí mismo al crearse). */
    private fun listenAgain() = speechInput.start()

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
                val context = if (diaryPending && !hideUserMessage) {
                    diaryPending = false
                    "Respondo a tu pregunta «¿qué tal el día?»: guárdalo en mi diario con saveDiaryEntry y contéstame en una frase."
                } else null
                val reply = apache.agent.send(conversationId, text, attachments, fromVoice, hideUserMessage, context)
                conversationId = reply.conversationId
                apache.settings.conversationId = reply.conversationId
                messages = messages + local(reply.text, isUser = false, images = reply.images)

                if (conversationMode && fromVoice) {
                    lastAnswer = reply.text
                    speakThenListen(reply.text)
                } else if (fromVoice && apache.settings.speakReplies) {
                    speaker.speak(reply.text)
                }
                onReply?.invoke(reply.text)
            } catch (e: GeminiException) {
                messages = messages + local(e.message ?: "Algo ha fallado.", isUser = false)
                if (conversationMode) speakThenListen(e.message ?: "Algo ha fallado.")
            } catch (e: Exception) {
                messages = messages + local("Algo ha fallado: ${e.message ?: e.javaClass.simpleName}", isUser = false)
                if (conversationMode) speakThenListen("Algo ha fallado, prueba otra vez.")
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

    /** Desde el aviso del diario: Apache pregunta qué tal el día. */
    fun askAboutDay() {
        diaryPending = true
        messages = messages + local("¿Qué tal el día? Cuéntamelo en un par de frases (o toca el micro) y lo apunto en tu diario 📓", isUser = false)
        shareCount++
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

    /**
     * Algo compartido desde otra app (Compartir → Apache): un enlace o texto
     * se pone en la caja de escribir con una pregunta sugerida; una imagen o
     * un PDF se adjunta. El usuario lo revisa y lo envía.
     */
    fun receiveShare(text: String?, uris: List<Uri>) {
        uris.take(5).forEach { addAttachment(it) }
        val shared = text?.trim().orEmpty()
        draft = when {
            shared.isEmpty() -> if (uris.isNotEmpty()) "¿Qué me dices de esto?" else ""
            Regex("^https?://\\S+$").matches(shared) -> "Resúmeme este enlace: $shared"
            shared.contains("http://") || shared.contains("https://") -> "Resúmeme esto: $shared"
            else -> "Sobre este texto:\n\n$shared\n\n"
        }
        shareCount++
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
        if (conversationMode) {
            endConversation()
            return
        }
        if (isListening) speechInput.stop() else {
            speaker.stop()
            speechInput.start()
        }
    }

    /** Lo que se ha oído por el micrófono (pulsación normal o modo conversación). */
    private fun onHeard(text: String) {
        if (!conversationMode) {
            send(text, fromVoice = true)
            return
        }
        silences = 0
        lastHeard = text
        if (isGoodbye(text)) {
            endConversation(say = "¡Hasta luego!")
            return
        }
        lastAnswer = ""
        send(text, fromVoice = true)
    }

    /**
     * Empieza la conversación manos libres: Apache escucha, responde en voz
     * alta y vuelve a escuchar solo, hasta que digas "para" o haya silencio.
     */
    fun startConversation() {
        if (!apache.settings.isConfigured) {
            notice = "Pon tu API key de Gemini en Ajustes para empezar."
            return
        }
        speechInput.stop()
        conversationMode = true
        silences = 0
        lastHeard = ""
        lastAnswer = ""
        speakThenListen("Dime.")
    }

    fun endConversation(say: String? = null) {
        conversationMode = false
        speechInput.stop()
        speaker.stop()
        isSpeaking = false
        if (say != null) speaker.speak(say)
    }

    /** Habla y, al terminar, vuelve a escuchar (si sigue el modo conversación). */
    private fun speakThenListen(text: String) {
        if (!conversationMode) return
        isSpeaking = true
        speaker.speak(text) {
            isSpeaking = false
            if (conversationMode && !isLoading) speechInput.start()
        }
    }

    private fun isGoodbye(text: String): Boolean {
        val clean = text.lowercase().trim().trimEnd('.', '!')
        return clean in setOf("para", "parar", "stop", "basta", "adiós", "adios", "hasta luego", "ya está", "ya esta", "nada más", "nada mas", "eso es todo", "gracias ya está", "gracias, ya está", "terminar", "salir") ||
            clean.startsWith("gracias, ya") || clean.startsWith("gracias ya") || clean.endsWith("eso es todo")
    }

    override fun onCleared() {
        conversationMode = false
        speechInput.stop()
        speaker.shutdown()
    }

    private companion object {
        const val MAX_ATTACHMENT = 10 * 1024 * 1024
    }
}
