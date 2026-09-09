package com.apache.ui

// Componentes básicos de Compose para construir la interfaz.
// Componentes Material 3.
// Estado y funciones de Compose.
// Alineación y estilos visuales.
// Jackson: convierte objetos Kotlin <-> JSON.
// Coroutines: permite hacer la petición HTTP sin congelar la interfaz.
// OkHttp: cliente HTTP para comunicarnos con Apache Core.
// Manejo de eventos de teclado (por ejemplo, Enter para enviar).
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

// Cliente HTTP que utilizará Apache Desktop.
private val httpClient = OkHttpClient()

// Conversor JSON.
private val objectMapper = jacksonObjectMapper()

// Tipo de contenido que enviamos al Core.
private val jsonMediaType = "application/json".toMediaType()

/*
 * Envía un mensaje al Apache Core.
 *
 * Desktop -> HTTP POST -> Core -> Agent -> Gemini
 *
 * Esta función se ejecuta fuera del hilo de la interfaz
 * para que la ventana no se quede congelada mientras esperamos.
 */
private fun sendMessageToCore(conversationId: String?, message: String): ChatResponse {

    // Convertimos nuestro ChatRequest a JSON.
    val json =
            objectMapper.writeValueAsString(
                    ChatRequest(conversationId = conversationId, message = message)
            )

    // Construimos la petición HTTP.
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

@Composable
fun App() {

    // Texto que actualmente está escrito en el campo de entrada.
    var message by remember { mutableStateOf("") }

    /*
     * ID de la conversación actual.
     *
     * Al principio es null.
     * El Core nos devolverá uno después del primer mensaje.
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

    /*
     * Coroutine scope utilizado para ejecutar la petición
     * sin bloquear la interfaz gráfica.
     */
    val scope = rememberCoroutineScope()

    MaterialTheme {

        // Fondo principal de la aplicación.
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {

            // Layout principal: barra lateral + chat.
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
                    Text(text = "Chat", color = Color.White, fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección Memoria.
                    Text(text = "Memoria", color = Color(0xFFAAAAAA), fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección Herramientas.
                    Text(text = "Herramientas", color = Color(0xFFAAAAAA), fontSize = 16.sp)

                    // Empuja la versión hacia la parte inferior.
                    Spacer(modifier = Modifier.weight(1f))

                    // Versión actual de Apache.
                    Text(text = "Apache 0.1.0", color = Color(0xFF666666), fontSize = 12.sp)
                }

                // =========================================================
                // ZONA PRINCIPAL DEL CHAT
                // =========================================================

                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

                    // Título.
                    Text(text = "Asistente", color = Color.White, fontSize = 22.sp)

                    Spacer(modifier = Modifier.height(20.dp))

                    // =====================================================
                    // CONVERSACIÓN
                    // =====================================================

                    LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        // Dibujamos todos los mensajes.
                        items(messages) { chatMessage -> MessageBubble(chatMessage) }

                        // Mientras esperamos al Core mostramos esto.
                        if (isLoading) {

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
                                placeholder = { Text("Escribe un mensaje...") },
                                singleLine = true,
                                // Desactivamos el campo mientras Apache piensa.
                                enabled = !isLoading,
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF1DB954),
                                                unfocusedBorderColor = Color(0xFF444444),
                                                cursorColor = Color(0xFF1DB954),
                                                focusedPlaceholderColor = Color(0xFF777777),
                                                unfocusedPlaceholderColor = Color(0xFF777777)
                                        )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // =================================================
                        // BOTÓN ENVIAR
                        // =================================================

                        Button(

                                // Desactivamos el botón mientras esperamos.
                                enabled = !isLoading,
                                onClick = {

                                    // Quitamos espacios al principio y final.
                                    val text = message.trim()

                                    // No hacemos nada si está vacío.
                                    if (text.isNotBlank()) {

                                        // Añadimos el mensaje del usuario al chat.
                                        messages = messages + ChatMessage(text, true)

                                        // Limpiamos el campo de texto.
                                        message = ""

                                        // Mostramos "Pensando...".
                                        isLoading = true

                                        /*
                                         * Ejecutamos la petición en un hilo
                                         * secundario para no bloquear Compose.
                                         */
                                        scope.launch(Dispatchers.IO) {
                                            try {

                                                // Enviamos el mensaje al Core.
                                                val response =
                                                        sendMessageToCore(conversationId, text)

                                                /*
                                                 * Volvemos al hilo principal
                                                 * para actualizar la interfaz.
                                                 */
                                                launch(Dispatchers.Main) {

                                                    // Guardamos el ID de conversación.
                                                    conversationId = response.conversationId

                                                    /*
                                                     * Normalmente recibiremos "reply".
                                                     *
                                                     * Si el Core solicita confirmación,
                                                     * utilizamos temporalmente el warning.
                                                     */
                                                    val reply =
                                                            response.reply
                                                                    ?: response.warning
                                                                            ?: "Apache no devolvió una respuesta."

                                                    // Añadimos la respuesta de Apache.
                                                    messages = messages + ChatMessage(reply, false)

                                                    // Dejamos de mostrar "Pensando...".
                                                    isLoading = false
                                                }
                                            } catch (e: Exception) {

                                                /*
                                                 * Si no podemos conectar con el Core,
                                                 * mostramos el error en el propio chat.
                                                 */
                                                launch(Dispatchers.Main) {
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
                                    }
                                },

                                // Color verde de Apache.
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1DB954)
                                        )
                        ) { Text(text = "Enviar", color = Color.Black) }
                    }
                }
            }
        }
    }
}

/*
 * Dibuja una burbuja de mensaje.
 *
 * Los mensajes del usuario aparecen a la derecha.
 * Los mensajes de Apache aparecen a la izquierda.
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
