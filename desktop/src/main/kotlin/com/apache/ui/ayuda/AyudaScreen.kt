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
        "Internet",
        listOf("«¿Quién ganó ayer el partido del Madrid?»", "«¿Qué estrenos hay este fin de semana?»")
    ),
    HelpSection(
        "Pantalla y archivos adjuntos",
        listOf(
            "«¿Qué hay en mi pantalla?» (hace la captura sola)",
            "Botón 📎 para adjuntar imágenes, PDF o texto y preguntar sobre ellos",
            "Botón 🖥 para adjuntar una captura de pantalla"
        )
    ),
    HelpSection(
        "Archivos del ordenador",
        listOf(
            "«Busca mis facturas en PDF»",
            "«Ábreme lo último que he descargado»",
            "«Enséñame dónde está el CV»"
        )
    ),
    HelpSection(
        "Casa (Smart Home)",
        listOf("«Enciende la luz del salón»", "«Apaga el enchufe del escritorio»", "«¿Qué dispositivos tengo?»")
    ),
    HelpSection(
        "Música",
        listOf(
            "«Pon música»",
            "«Pon Bohemian Rhapsody de Queen»",
            "«Busca la canción Despacito»",
            "«Pon Shakira en Spotify»",
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
    HelpSection("Tiempo", listOf("«¿Qué tiempo hace mañana?»")),
    HelpSection(
        "Imágenes",
        listOf(
            "«Enséñame un ajolote»",
            "«¿Cómo es la Sagrada Familia?»",
            "«Muéstrame tres razas de perro pequeñas»",
            "Las imágenes no se guardan solas: haz clic en una o di «guarda la 2» / «guárdalas todas»"
        )
    ),
    HelpSection(
        "Memoria",
        listOf(
            "«Recuerda que me llamo Fran»",
            "«Mi comida favorita es la pizza»",
            "«¿Qué sabes de mí?»",
            "«Olvida mi comida favorita»"
        )
    ),
    HelpSection(
        "Horario",
        listOf(
            "«Organízame el día: estudiar 3 h, gimnasio y compra»",
            "«Hazme un horario para mañana de 9 a 18»",
            "«¿Qué tengo hoy?»"
        )
    )
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
                "• Música: poner una canción concreta (Spotify si lo conectas, si no YouTube), reproducir, pausar, cambiar de pista, consultar lo que suena y ajustar el volumen.\n" +
                "• Tiempo: consultar el tiempo actual y la previsión.\n" +
                "• Sistema: ver recursos e información del ordenador.\n" +
                "• Fecha y hora: consultar la hora actual.\n" +
                "• Imágenes: enseñar de 1 a 3 imágenes en el chat cuando quieres ver algo.\n" +
                "• Horario: organizar un día en bloques (sección Horario o pidiéndoselo en el chat).\n" +
                "• Memoria: recordar datos sobre ti entre conversaciones (sección Memoria para verlos y editarlos).\n" +
                "• Internet: buscar noticias, resultados y cualquier dato actual.\n" +
                "• Pantalla y adjuntos: ver tu pantalla, imágenes, PDFs o textos y responder sobre ellos.\n" +
                "• Archivos: buscar, ver los recientes y abrir archivos de tus carpetas.\n" +
                "• Casa: encender y apagar dispositivos por MQTT (si los configuras).\n" +
                "• Al abrir Apache por primera vez cada día te da un resumen del día.\n" +
                "• Las acciones delicadas (como borrar un evento) piden confirmación con botones Sí / No.",
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

        Text(text = "Conectar Spotify (opcional, necesita Premium)", color = ApacheColors.accent, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "1. Entra en developer.spotify.com, inicia sesión y crea una app (Create app).\n" +
                "2. En Redirect URIs pon exactamente: http://127.0.0.1:8080/api/spotify/callback\n" +
                "3. Marca «Web API», guarda y copia el Client ID.\n" +
                "4. En PowerShell: setx SPOTIFY_CLIENT_ID \"tu_client_id\" y vuelve a abrir Apache.\n" +
                "5. Dile a Apache «conecta mi Spotify» y acepta en el navegador. Solo hay que hacerlo una vez.\n" +
                "Sin esto, Apache pone las canciones en YouTube.",
            color = ApacheColors.textCalendarBody,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Configurar la casa (opcional)", color = ApacheColors.accent, fontSize = 17.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Copia docs/smarthome.example.json del proyecto a la carpeta .apache de tu usuario " +
                "con el nombre smarthome.json, y pon la dirección de tu broker MQTT y tus dispositivos " +
                "(nombre y topic). No hace falta reiniciar Apache.",
            color = ApacheColors.textCalendarBody,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

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
