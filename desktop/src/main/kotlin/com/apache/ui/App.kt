package com.apache.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.audio.AudioPlayer
import com.apache.audio.MicrophoneRecorder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64

// Representa un mensaje que aparece en el chat.
data class ChatMessage(val text: String, val isUser: Boolean)

// DTO que enviamos al Core.
data class ChatRequest(val conversationId: String? = null, val message: String)

// DTO que recibimos del Core.
data class ChatResponse(
        val conversationId: String,
        val reply: String? = null,
        val needsConfirmation: Boolean = false,
        val confirmationId: String? = null,
        val warning: String? = null
)

// DTO de un turno del historial de una conversación.
data class ChatHistoryMessage(val role: String, val content: String)

// DTO que recibimos del endpoint de historial de conversación.
data class ChatHistoryResponse(
        val conversationId: String,
        val exists: Boolean,
        val messages: List<ChatHistoryMessage>
)

// DTO de un recuerdo guardado sobre el usuario.
data class MemoryItem(val id: Int, val content: String)

// DTO que recibimos del endpoint de transcripción.
data class VoiceTranscriptionResponse(val text: String)

// DTO que enviamos al endpoint de síntesis de voz.
data class VoiceSpeakRequest(val text: String)

// DTO que recibimos del endpoint de síntesis de voz.
data class VoiceSpeechResponse(val audio: String, val sampleRate: Int)

// Palabra de activación que Apache reconoce en el modo escucha.
private val wakeWordRegex = Regex("(?i)\\bapache\\b")

// Cliente HTTP que utilizará Apache Desktop.
private val httpClient = OkHttpClient()

// Conversor JSON.
private val objectMapper = jacksonObjectMapper()

// Tipo de contenido que enviamos al Core.
private val jsonMediaType = "application/json".toMediaType()

/**
 * Envía un mensaje al Apache Core.
 *
 * Desktop -> HTTP POST -> Core -> Agent -> Gemini
 *
 * Esta función se ejecuta fuera del hilo de la interfaz para que la ventana no se quede congelada
 * mientras esperamos.
 */
private fun sendMessageToCore(conversationId: String?, message: String): ChatResponse {

    // Convertimos nuestro ChatRequest a JSON.
    val json =
            objectMapper.writeValueAsString(
                    ChatRequest(conversationId = conversationId, message = message)
            )

    // Construimos la petición.
    val request =
            Request.Builder()
                    .url("http://localhost:8080/api/chat")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()

    // Ejecutamos la petición y esperamos la respuesta.
    httpClient.newCall(request).execute().use { response ->

        // Si el Core devuelve un error HTTP, lanzamos una excepción.
        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        // Obtenemos el cuerpo de la respuesta.
        val responseBody =
                response.body?.string() ?: throw Exception("El Core no devolvió ninguna respuesta.")

        // Convertimos el JSON recibido en ChatResponse.
        return objectMapper.readValue(responseBody)
    }
}

/**
 * Envía una grabación al Apache Core para convertirla en texto.
 *
 * Desktop -> HTTP POST -> Core -> SpeechToTextClient -> Gemini
 */
private fun transcribeAudio(audioData: ByteArray): VoiceTranscriptionResponse {

    // Creamos el cuerpo binario con el audio PCM.
    val audioBody = audioData.toRequestBody("audio/L16;rate=16000".toMediaType())

    // Construimos la petición multipart.
    val requestBody =
            MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("audio", "recording.pcm", audioBody)
                    .build()

    // Construimos la petición.
    val request =
            Request.Builder()
                    .url("http://localhost:8080/api/voice/transcribe")
                    .post(requestBody)
                    .build()

    // Ejecutamos la petición.
    httpClient.newCall(request).execute().use { response ->

        // Si el Core devuelve un error HTTP, lanzamos una excepción.
        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        // Obtenemos el cuerpo de la respuesta.
        val responseBody =
                response.body?.string()
                        ?: throw Exception("El Core no devolvió ninguna transcripción.")

        // Convertimos la respuesta en nuestro DTO.
        return objectMapper.readValue(responseBody)
    }
}

/**
 * Solicita al Core que convierta un texto en audio (Text-to-Speech).
 *
 * Desktop -> HTTP POST -> Core -> TextToSpeechClient -> Gemini
 */
private fun synthesizeSpeech(text: String): VoiceSpeechResponse {

    val json = objectMapper.writeValueAsString(VoiceSpeakRequest(text = text))

    val request =
            Request.Builder()
                    .url("http://localhost:8080/api/voice/speak")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()

    httpClient.newCall(request).execute().use { response ->

        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        val responseBody =
                response.body?.string() ?: throw Exception("El Core no devolvió ningún audio.")

        return objectMapper.readValue(responseBody)
    }
}

/**
 * Fichero donde se guarda el id de la última conversación activa, para poder retomarla la
 * próxima vez que se abra la app en vez de empezar siempre de cero (ver [loadSavedConversationId]
 * y [saveConversationId]). Vive en la carpeta de usuario, junto al resto de configuración local
 * de Apache.
 */
private val lastConversationFile =
        File(File(System.getProperty("user.home"), ".apache"), "last_conversation_id.txt")

/** Recupera el id de la última conversación guardada, o null si no hay ninguna (primer arranque). */
private fun loadSavedConversationId(): String? =
        try {
            lastConversationFile.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
        } catch (_: Exception) {
            null
        }

/** Guarda el id de la conversación actual para poder retomarla en el próximo arranque. */
private fun saveConversationId(conversationId: String) {
    try {
        lastConversationFile.parentFile?.mkdirs()
        lastConversationFile.writeText(conversationId)
    } catch (_: Exception) {
        // Si no se puede persistir (permisos, disco lleno...), Apache simplemente empezará una
        // conversación nueva la próxima vez. No es un fallo crítico.
    }
}

/**
 * Recupera el historial de una conversación existente desde el Core, para poder repoblar el
 * chat al reabrir la app. Devuelve null si la conversación ya no existe o si el Core no
 * responde (por ejemplo, si aún no ha terminado de arrancar).
 */
private fun fetchConversationHistory(conversationId: String): ChatHistoryResponse? {
    val request =
            Request.Builder()
                    .url("http://localhost:8080/api/chat/history/$conversationId")
                    .get()
                    .build()

    return try {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            objectMapper.readValue<ChatHistoryResponse>(body)
        }
    } catch (_: Exception) {
        null
    }
}

/** Recupera todos los recuerdos guardados sobre el usuario, para mostrarlos en la sección Memoria. */
private fun fetchMemories(): List<MemoryItem> {
    val request = Request.Builder().url("http://localhost:8080/api/memory").get().build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("El Core respondió con HTTP ${response.code}")
        }

        val body = response.body?.string() ?: return emptyList()
        return objectMapper.readValue(body)
    }
}

/** Borra un recuerdo concreto por id. Devuelve true si se ha borrado correctamente. */
private fun deleteMemory(id: Int): Boolean {
    val request =
            Request.Builder().url("http://localhost:8080/api/memory/$id").delete().build()

    httpClient.newCall(request).execute().use { response -> return response.isSuccessful }
}

/**
 * Convierte un texto en audio y lo reproduce por los altavoces.
 *
 * Es una llamada bloqueante (red + reproducción), así que debe ejecutarse siempre desde un hilo
 * secundario, nunca desde el hilo de la interfaz. Si la síntesis de voz falla, no interrumpe la
 * conversación: la respuesta ya se ha mostrado en texto.
 */
private fun speakReply(text: String, audioPlayer: AudioPlayer) {

    if (text.isBlank()) {
        return
    }

    try {
        val speech = synthesizeSpeech(text)

        if (speech.audio.isNotBlank()) {
            val audioBytes = Base64.getDecoder().decode(speech.audio)
            audioPlayer.play(audioBytes, speech.sampleRate)
        }
    } catch (_: Exception) {
        // Fallo silencioso: la respuesta de texto ya se ha mostrado igualmente.
    }
}

@Composable
fun App() {

    // Texto que actualmente está escrito en el campo de entrada.
    var message by remember { mutableStateOf("") }

    /**
     * ID de la conversación actual.
     *
     * Al principio es null. El Core nos devolverá uno después del primer mensaje.
     *
     * Así Apache puede mantener el contexto de la conversación.
     */
    var conversationId by remember { mutableStateOf<String?>(null) }

    // Lista de mensajes que aparecen en pantalla.
    var messages by remember {
        mutableStateOf(
                listOf(
                        // Mensaje inicial de Apache.
                        ChatMessage("Hola. Soy Apache. ¿En qué puedo ayudarte?", false)
                )
        )
    }

    // Indica si Apache está esperando una respuesta del Core.
    var isLoading by remember { mutableStateOf(false) }

    // Indica si debemos mostrar "Pensando..." mientras esperamos la respuesta del Core.
    var showThinking by remember { mutableStateOf(false) }

    // Controla qué sección de la interfaz está seleccionada.
    var selectedSection by remember { mutableStateOf("Chat") }

    // Coroutine scope utilizado para ejecutar la petición sin bloquear la interfaz gráfica.
    val scope = rememberCoroutineScope()

    // Controla la posición del scroll de la conversación.
    val chatListState = rememberLazyListState()

    // Controla la grabación y reproducción de audio.
    val microphoneRecorder = remember { MicrophoneRecorder() }
    val audioPlayer = remember { AudioPlayer() }

    // Indica si Apache está grabando desde el micrófono.
    var isRecording by remember { mutableStateOf(false) }

    // Indica si el modo escucha (wake word "Apache") está activado.
    var listenModeEnabled by remember { mutableStateOf(false) }

    // Guarda la última grabación realizada.
    var recordedAudio by remember { mutableStateOf<ByteArray?>(null) }

    // Mantiene el chat desplazado hasta el último mensaje.
    LaunchedEffect(messages.size, showThinking) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun processMessage(text: String, speak: Boolean = false) {

        if (text.isBlank() || isLoading) {
            return
        }

        messages = messages + ChatMessage(text, true)

        isLoading = true
        showThinking = false

        scope.launch {
            delay(400)

            if (isLoading) {
                showThinking = true
            }
        }

        scope.launch(Dispatchers.IO) {
            try {

                val response = sendMessageToCore(conversationId, text)

                val reply =
                        response.reply
                                ?: response.warning ?: "Apache no devolvió una respuesta."

                launch(Dispatchers.Main) {
                    conversationId = response.conversationId

                    messages = messages + ChatMessage(reply, false)

                    isLoading = false
                    showThinking = false
                }

                // Si el mensaje viene de una interacción por voz, Apache responde también hablando.
                if (speak) {
                    speakReply(reply, audioPlayer)
                }
            } catch (e: Exception) {

                launch(Dispatchers.Main) {
                    messages =
                            messages +
                                    ChatMessage(
                                            "No puedo conectar con Apache Core: ${e.message}",
                                            false
                                    )

                    isLoading = false
                    showThinking = false
                }
            }
        }
    }
    fun processRecordedAudio(audio: ByteArray) {

        scope.launch(Dispatchers.IO) {
            try {

                val transcription = transcribeAudio(audio)

                val text = transcription.text.trim()

                launch(Dispatchers.Main) {
                    if (text.isNotBlank()) {

                        processMessage(text, speak = true)
                    } else {

                        messages =
                                messages +
                                        ChatMessage(
                                                "No he podido reconocer lo que has dicho.",
                                                false
                                        )
                    }
                }
            } catch (e: Exception) {

                launch(Dispatchers.Main) {
                    messages =
                            messages +
                                    ChatMessage(
                                            "No se ha podido transcribir el audio: ${e.message}",
                                            false
                                    )
                }
            }
        }
    }
    // Inicia o detiene la grabación del micrófono.
    fun toggleRecording() {

        if (isRecording) {

            scope.launch(Dispatchers.IO) {
                try {

                    val audio = microphoneRecorder.stop()

                    launch(Dispatchers.Main) { isRecording = false }

                    if (audio.isNotEmpty()) {
                        processRecordedAudio(audio)
                    }
                } catch (e: Exception) {

                    launch(Dispatchers.Main) {
                        isRecording = false

                        messages =
                                messages +
                                        ChatMessage(
                                                "No se ha podido detener la grabación: ${e.message}",
                                                false
                                        )
                    }
                }
            }

            return
        }

        isRecording = true

        scope.launch(Dispatchers.IO) {
            try {

                microphoneRecorder.start { audio ->
                    scope.launch(Dispatchers.Main) { isRecording = false }

                    processRecordedAudio(audio)
                }
            } catch (e: Exception) {

                launch(Dispatchers.Main) {
                    isRecording = false

                    messages =
                            messages +
                                    ChatMessage(
                                            "No se ha podido acceder al micrófono: ${e.message}",
                                            false
                                    )
                }
            }
        }
    }
    // Activa o desactiva el modo escucha (wake word "Apache").
    fun toggleListenMode() {
        listenModeEnabled = !listenModeEnabled
    }

    /**
     * Ejecuta un turno completo de voz: manda el texto ya transcrito al Core (mismo Agent que el
     * chat de texto), muestra la respuesta y la reproduce por los altavoces (TTS).
     *
     * Es una función suspend que no termina hasta que la respuesta se ha mostrado y se ha
     * terminado de reproducir por voz, para poder encadenar turnos del modo escucha sin que
     * Apache se grabe a sí mismo mientras habla.
     */
    suspend fun runVoiceTurn(text: String) {

        withContext(Dispatchers.Main) {
            messages = messages + ChatMessage(text, true)
            isLoading = true
        }

        try {
            val response = sendMessageToCore(conversationId, text)

            val reply =
                    response.reply ?: response.warning ?: "Apache no devolvió una respuesta."

            withContext(Dispatchers.Main) {
                conversationId = response.conversationId
                messages = messages + ChatMessage(reply, false)
                isLoading = false
            }

            speakReply(reply, audioPlayer)
        } catch (e: Exception) {

            withContext(Dispatchers.Main) {
                messages =
                        messages +
                                ChatMessage(
                                        "No puedo conectar con Apache Core: ${e.message}",
                                        false
                                )
                isLoading = false
            }
        }
    }

    /**
     * Escucha en segundo plano hasta detectar la palabra de activación "Apache".
     *
     * Aprovecha que MicrophoneRecorder ya graba hasta 3 segundos de silencio: cada grabación se
     * transcribe y se comprueba si contiene "Apache". Si el usuario ha dicho el comando en la
     * misma frase ("Apache, abre Discord"), se procesa directamente. Si solo ha dicho "Apache",
     * se vuelve a llamar a esta misma función con [awaitingCommand] = true para grabar el
     * comando a continuación, tratando toda la siguiente grabación como el comando.
     *
     * Es una única función auto-recursiva (en vez de dos funciones que se llaman entre sí) para
     * evitar referencias hacia adelante entre funciones locales, que Kotlin no permite.
     */
    fun startPassiveListening(awaitingCommand: Boolean = false) {

        if (!listenModeEnabled) {
            return
        }

        microphoneRecorder.start { audio ->
            scope.launch(Dispatchers.IO) {

                if (audio.isEmpty()) {
                    if (listenModeEnabled) startPassiveListening(awaitingCommand)
                    return@launch
                }

                try {
                    val transcription = transcribeAudio(audio)
                    val transcript = transcription.text.trim()

                    if (awaitingCommand) {

                        // Ya se detectó "Apache" antes: esta grabación es directamente el comando.
                        if (transcript.isNotBlank()) {
                            runVoiceTurn(transcript)
                        }

                        if (listenModeEnabled) {
                            startPassiveListening()
                        }
                    } else {

                        val match = wakeWordRegex.find(transcript)

                        if (match == null) {

                            // No se ha dicho "Apache": descartamos el audio y seguimos escuchando.
                            if (listenModeEnabled) startPassiveListening()
                            return@launch
                        }

                        val command =
                                transcript
                                        .substring(match.range.last + 1)
                                        .trim()
                                        .trimStart(',', '.', ':', ';', '-')
                                        .trim()

                        if (command.isNotBlank()) {

                            runVoiceTurn(command)

                            if (listenModeEnabled) {
                                startPassiveListening()
                            }
                        } else {

                            // Solo se ha dicho "Apache": grabamos el comando por separado.
                            startPassiveListening(awaitingCommand = true)
                        }
                    }
                } catch (_: Exception) {

                    // Ignoramos errores puntuales de la escucha pasiva (ruido, silencio, etc.)
                    // y seguimos escuchando en segundo plano.
                    if (listenModeEnabled) startPassiveListening(awaitingCommand)
                }
            }
        }
    }

    // Inicia o detiene el bucle de escucha pasiva en cuanto cambia el modo escucha.
    LaunchedEffect(listenModeEnabled) {
        if (listenModeEnabled) {
            startPassiveListening()
        } else {
            withContext(Dispatchers.IO) { microphoneRecorder.stop() }
        }
    }

    // Función que envía el mensaje tanto desde el botón como desde Enter.
    fun sendMessage() {

        val text = message.trim()

        if (text.isNotBlank() && !isLoading) {

            message = ""

            processMessage(text)
        }
    }

    MaterialTheme {

        // Fondo principal de la aplicación.
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {

            // Layout principal: barra lateral + contenido.
            Row(modifier = Modifier.fillMaxSize()) {

                // =========================================================
                // BARRA LATERAL
                // =========================================================

                Column(
                        modifier =
                                Modifier.width(220.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFF181818))
                                        .padding(20.dp)
                ) {

                    // Nombre de Apache.
                    Text(text = "APACHE", color = Color(0xFF1DB954), fontSize = 24.sp)

                    Spacer(modifier = Modifier.height(30.dp))

                    // Sección Chat.
                    Text(
                            text = "Chat",
                            color =
                                    if (selectedSection == "Chat") {
                                        Color.White
                                    } else {
                                        Color(0xFFAAAAAA)
                                    },
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { selectedSection = "Chat" }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección Memoria.
                    Text(
                            text = "Memoria",
                            color =
                                    if (selectedSection == "Memoria") {
                                        Color.White
                                    } else {
                                        Color(0xFFAAAAAA)
                                    },
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { selectedSection = "Memoria" }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección Herramientas.
                    Text(
                            text = "Herramientas",
                            color =
                                    if (selectedSection == "Herramientas") {
                                        Color.White
                                    } else {
                                        Color(0xFFAAAAAA)
                                    },
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { selectedSection = "Herramientas" }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección Ayuda.
                    Text(
                            text = "Ayuda",
                            color =
                                    if (selectedSection == "Ayuda") {
                                        Color.White
                                    } else {
                                        Color(0xFFAAAAAA)
                                    },
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { selectedSection = "Ayuda" }
                    )

                    // Empuja la versión hacia la parte inferior.
                    Spacer(modifier = Modifier.weight(1f))

                    // Versión actual de Apache.
                    Text(text = "Apache 0.1.0", color = Color(0xFF666666), fontSize = 12.sp)
                }

                // =========================================================
                // CONTENIDO PRINCIPAL
                // =========================================================

                when (selectedSection) {

                    // =====================================================
                    // CHAT
                    // =====================================================

                    "Chat" -> {

                        // Zona principal del chat.
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

                            // Título.
                            Text(text = "Asistente", color = Color.White, fontSize = 22.sp)

                            Spacer(modifier = Modifier.height(20.dp))

                            // =====================================================
                            // CONVERSACIÓN
                            // =====================================================

                            LazyColumn(
                                    state = chatListState,
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                // Dibujamos todos los mensajes.
                                items(messages) { chatMessage -> MessageBubble(chatMessage) }

                                // Mientras esperamos al Core mostramos esto.
                                if (showThinking) {
                                    item { MessageBubble(ChatMessage("Pensando...", false)) }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // =====================================================
                            // ENTRADA DE MENSAJE
                            // =====================================================

                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {

                                // Campo donde escribe el usuario.
                                OutlinedTextField(
                                        value = message,
                                        onValueChange = { message = it },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions =
                                                KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions =
                                                KeyboardActions(onSend = { sendMessage() }),
                                        placeholder = { Text("Escribe un mensaje...") },
                                        singleLine = true,

                                        // Desactivamos el campo mientras Apache piensa.
                                        enabled = !isLoading && !isRecording,
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        focusedBorderColor = Color(0xFF1DB954),
                                                        unfocusedBorderColor = Color(0xFF444444),
                                                        cursorColor = Color(0xFF1DB954),
                                                        focusedPlaceholderColor = Color(0xFF777777),
                                                        unfocusedPlaceholderColor =
                                                                Color(0xFF777777)
                                                )
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // =================================================
                                // BOTÓN DE MODO ESCUCHA (wake word)
                                // =================================================

                                Button(
                                        enabled = !isLoading && !isRecording,
                                        onClick = { toggleListenMode() },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                if (listenModeEnabled) {
                                                                    Color(0xFF1DB954)
                                                                } else {
                                                                    Color(0xFF444444)
                                                                }
                                                )
                                ) {
                                    Text(
                                            text =
                                                    if (listenModeEnabled) {
                                                        "Escucha activada"
                                                    } else {
                                                        "Escucha desactivada"
                                                    },
                                            color = Color.Black
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // =================================================
                                // BOTÓN DE VOZ
                                // =================================================

                                Button(
                                        enabled = !isLoading && !listenModeEnabled,
                                        onClick = { toggleRecording() },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                if (isRecording) {
                                                                    Color(0xFFAA2222)
                                                                } else {
                                                                    Color(0xFF1DB954)
                                                                }
                                                )
                                ) {
                                    Text(
                                            text =
                                                    if (isRecording) {
                                                        "Detener"
                                                    } else {
                                                        "Micrófono"
                                                    },
                                            color = Color.Black
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // =================================================
                                // BOTÓN ENVIAR
                                // =================================================

                                Button(
                                        // Desactivamos el botón mientras esperamos.
                                        enabled = !isLoading && !isRecording,

                                        // Utilizamos la misma función que Enter.
                                        onClick = { sendMessage() },

                                        // Color verde de Apache.
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF1DB954)
                                                )
                                ) { Text(text = "Enviar", color = Color.Black) }
                            }
                        }
                    }

                    // =====================================================
                    // MEMORIA
                    // =====================================================

                    "Memoria" -> {

                        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

                            // Título de la sección.
                            Text(text = "Memoria", color = Color.White, fontSize = 22.sp)

                            Spacer(modifier = Modifier.height(20.dp))

                            // Estado actual de la sección.
                            Text(
                                    text = "La memoria de Apache estará disponible próximamente.",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 15.sp
                            )
                        }
                    }

                    // =====================================================
                    // HERRAMIENTAS
                    // =====================================================

                    "Herramientas" -> {

                        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

                            // Título de la sección.
                            Text(text = "Herramientas", color = Color.White, fontSize = 22.sp)

                            Spacer(modifier = Modifier.height(20.dp))

                            // Herramienta de tiempo.
                            Text(text = "Tiempo", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "Consulta el tiempo actual y la previsión.",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Herramienta de música.
                            Text(text = "Música", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "Controla la reproducción y el volumen.",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Herramienta de aplicaciones.
                            Text(text = "Aplicaciones", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "Abre, cierra y consulta aplicaciones.",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Herramienta de sistema.
                            Text(text = "Sistema", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "Consulta los recursos del ordenador.",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 15.sp
                            )
                        }
                    }

                    // =====================================================
                    // AYUDA
                    // =====================================================

                    "Ayuda" -> {

                        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

                            // Título de la sección.
                            Text(text = "Ayuda", color = Color.White, fontSize = 22.sp)

                            Spacer(modifier = Modifier.height(20.dp))

                            // Introducción.
                            Text(
                                    text = "¿Qué puedo pedirle a Apache?",
                                    color = Color.White,
                                    fontSize = 17.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                    text = "Puedes pedirle acciones utilizando lenguaje natural.",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // =================================================
                            // EJEMPLOS DE MÚSICA
                            // =================================================

                            Text(text = "Música", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(text = "«Pon música»", color = Color(0xFFCCCCCC), fontSize = 15.sp)

                            Text(
                                    text = "«Pausa la música»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Text(
                                    text = "«Sube el volumen»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Text(
                                    text = "«¿Qué está sonando?»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // =================================================
                            // EJEMPLOS DE APLICACIONES
                            // =================================================

                            Text(text = "Aplicaciones", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "«Abre Discord»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Text(
                                    text = "«Abre Discord y la calculadora»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Text(
                                    text = "«Cierra Discord»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Text(
                                    text = "«¿Está abierto Visual Studio Code?»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // =================================================
                            // EJEMPLOS DE SISTEMA
                            // =================================================

                            Text(text = "Sistema", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "«¿Qué recursos está usando mi PC?»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // =================================================
                            // EJEMPLOS DE TIEMPO
                            // =================================================

                            Text(text = "Tiempo", color = Color(0xFF1DB954), fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "«¿Qué tiempo hace mañana?»",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Próximas funcionalidades.
                            Text(text = "Próximamente", color = Color.White, fontSize = 17.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                    text = "Control por voz",
                                    color = Color(0xFF777777),
                                    fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dibuja una burbuja de mensaje.
 *
 * Los mensajes del usuario aparecen a la derecha. Los mensajes de Apache aparecen a la izquierda.
 */
@Composable
fun MessageBubble(message: ChatMessage) {

    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                    if (message.isUser) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
    ) {
        Surface(
                color =
                        if (message.isUser) {
                            Color(0xFF1DB954)
                        } else {
                            Color(0xFF242424)
                        },
                shape = RoundedCornerShape(12.dp)
        ) {

            // Permite seleccionar y copiar el texto del mensaje.
            SelectionContainer {
                Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color =
                                if (message.isUser) {
                                    Color.Black
                                } else {
                                    Color.White
                                },
                        fontSize = 15.sp
                )
            }
        }
    }
}
