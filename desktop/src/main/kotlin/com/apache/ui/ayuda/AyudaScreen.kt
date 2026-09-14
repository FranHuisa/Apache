package com.apache.ui.ayuda

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.ui.theme.ApacheColors

/** Un bloque de "Ayuda" con un título destacado y una lista de ejemplos de órdenes. */
private data class HelpSection(val title: String, val examples: List<String>)

private val helpSections = listOf(
    HelpSection(
        "Música",
        listOf(
            "«Pon música»",
            "«Pausa la música»",
            "«Sube el volumen»",
            "«¿Qué está sonando?»",
            "«Pon el volumen al 40 %»"
        )
    ),
    HelpSection(
        "Aplicaciones",
        listOf(
            "«Abre Discord»",
            "«Abre Discord y la calculadora»",
            "«Cierra Discord»",
            "«¿Está abierto Visual Studio Code?»"
        )
    ),
    HelpSection("Sistema", listOf("«¿Qué recursos está usando mi PC?»")),
    HelpSection("Tiempo", listOf("«¿Qué tiempo hace mañana?»"))
)

@Composable
fun AyudaScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "Ayuda", color = Color.White, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "¿Qué puedo pedirle a Apache?", color = Color.White, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Escribe o habla con naturalidad. Apache elige la herramienta adecuada y te " +
                "pide confirmación antes de acciones sensibles.",
            color = ApacheColors.textMuted,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Qué puede hacer", color = Color.White, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "• Aplicaciones: abrir, cerrar o comprobar si una aplicación está abierta.\n" +
                "• Música: reproducir, pausar, cambiar de pista, consultar lo que suena y ajustar el volumen.\n" +
                "• Tiempo: consultar el tiempo actual y la previsión.\n" +
                "• Sistema: ver recursos e información del ordenador.\n" +
                "• Fecha y hora: consultar la hora actual.",
            color = ApacheColors.textCalendarBody,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        helpSections.forEach { section ->
            Text(text = section.title, color = ApacheColors.accent, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))

            section.examples.forEach { example ->
                Text(text = example, color = ApacheColors.textCalendarBody, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(text = "Control por voz", color = ApacheColors.accent, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Pulsa el micrófono para una orden puntual. Activa «Escucha activada» para hablar sin " +
                "tocar el botón: di «Apache» seguido de la orden, por ejemplo «Apache, abre Discord». El " +
                "modo sigue activo después de cada respuesta. Para detenerlo, di «Apache, apaga» o " +
                "«Apache, corto», o pulsa el botón de escucha.",
            color = ApacheColors.textCalendarBody,
            fontSize = 15.sp
        )
    }
}
