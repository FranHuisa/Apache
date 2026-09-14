package com.apache.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.apache.ui.ayuda.AyudaScreen
import com.apache.ui.calendar.CalendarController
import com.apache.ui.calendar.CalendarScreen
import com.apache.ui.chat.ChatController
import com.apache.ui.chat.ChatScreen
import com.apache.ui.components.NotificationsOverlay
import com.apache.ui.components.Sidebar
import com.apache.ui.memoria.MemoriaScreen
import com.apache.ui.notifications.NotificationsController
import com.apache.ui.theme.ApacheColors
import com.apache.ui.voice.VoiceController
import androidx.compose.ui.unit.dp
/**
 * Punto de entrada de la interfaz de Apache Desktop.
 *
 * Esta función ya no contiene lógica de negocio: solo crea los controllers
 * de cada dominio (chat, voz, calendario, notificaciones), los conecta entre
 * sí donde hace falta, y decide qué pantalla dibujar según la sección elegida
 * en la barra lateral.
 */
@Composable
fun App() {
    val scope = rememberCoroutineScope()

    val chatController = remember { ChatController(scope) }
    val calendarController = remember { CalendarController(scope) }
    val notificationsController = remember { NotificationsController(scope) }

    // El VoiceController necesita poder hablar la respuesta de un turno de chat,
    // pero ChatController no debe conocer a VoiceController (evitamos acoplar
    // "texto" con "voz"). Por eso el propio VoiceController se referencia a sí
    // mismo desde su callback una vez construido.
    lateinit var voiceControllerRef: VoiceController
    val voiceController = remember {
        VoiceController(
            scope = scope,
            onCommand = { text, speak ->
                chatController.runTurn(text, speak) { reply -> voiceControllerRef.speak(reply) }
            },
            onSystemMessage = { chatController.appendSystemMessage(it) }
        ).also { voiceControllerRef = it }
    }

    var selectedSection by remember { mutableStateOf("Chat") }

    LaunchedEffect(Unit) { notificationsController.startPolling() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = ApacheColors.background) {
            Box(modifier = Modifier.fillMaxSize()) {

                Row(modifier = Modifier.fillMaxSize()) {

                    Sidebar(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it }
                    )

                    when (selectedSection) {
                        "Chat" -> ChatScreen(chatController, voiceController)
                        "Calendario" -> CalendarScreen(calendarController)
                        "Memoria" -> MemoriaScreen()
                        "Ayuda" -> AyudaScreen()
                    }
                }

                Box(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                    NotificationsOverlay(
                        notifications = notificationsController.pending,
                        onDismiss = { notificationsController.dismiss(it) }
                    )
                }
            }
        }
    }
}
