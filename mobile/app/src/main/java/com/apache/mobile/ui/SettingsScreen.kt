package com.apache.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.Settings
import com.apache.mobile.reminders.Briefing
import com.apache.mobile.reminders.Notifications
import com.apache.mobile.reminders.Proactive
import java.time.LocalTime
import kotlinx.coroutines.launch

/** Ajustes: API key de Gemini, modelo, voz y resumen de buenos días. */
@Composable
fun SettingsScreen(onSaved: () -> Unit) {
    val settings = ApacheApp.get().settings
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var speak by remember { mutableStateOf(settings.speakReplies) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var briefingOn by remember { mutableStateOf(settings.briefingEnabled) }
    var briefingTime by remember { mutableStateOf(settings.briefingTime) }
    var testing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var diaryOn by remember { mutableStateOf(settings.diaryEnabled) }
    var diaryTime by remember { mutableStateOf(settings.diaryTime) }
    var proactiveOn by remember { mutableStateOf(settings.proactiveEnabled) }
    var checking by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Ajustes", color = Color.White, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text("API key de Gemini", color = ApacheColors.accent, fontSize = 15.sp)
        Text(
            "Es la misma que usa Apache en el PC. Se guarda solo en este móvil.",
            color = ApacheColors.textMuted, fontSize = 13.sp
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("AIza...") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Ocultar" else "Ver", fontSize = 12.sp) }
            }
        )
        // Aviso si lo pegado no tiene pinta de clave de Gemini (AIza..., ~39 caracteres).
        val cleaned = Settings.cleanApiKey(apiKey)
        if (apiKey.isNotBlank() && !Settings.looksLikeGeminiKey(cleaned)) {
            Text(
                "Esto no parece una clave de Gemini: debe empezar por «AIza» (o «AQ.») y no llevar espacios.",
                color = ApacheColors.danger, fontSize = 13.sp
            )
        } else if (apiKey.isNotBlank() && cleaned != apiKey.trim()) {
            Text("Se guardará solo la clave (sin comillas ni texto de más).", color = ApacheColors.accentLight, fontSize = 13.sp)
        }

        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
        }) { Text("Conseguir una API key (gratis) ↗", color = ApacheColors.accentLight) }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Modelo", color = ApacheColors.accent, fontSize = 15.sp)
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Por defecto ${Settings.DEFAULT_MODEL}") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Leer las respuestas en voz alta", color = Color.White, fontSize = 15.sp)
                Text("Cuando le hablas con el micrófono.", color = ApacheColors.textMuted, fontSize = 13.sp)
            }
            Switch(
                checked = speak,
                onCheckedChange = { speak = it; saved = false },
                colors = SwitchDefaults.colors(checkedTrackColor = ApacheColors.accent)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Resumen de buenos días: se guarda al momento.
        Text("Resumen de buenos días", color = ApacheColors.accent, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Notificación cada mañana", color = Color.White, fontSize = 15.sp)
                Text("Agenda, tiempo, listas y 3 titulares.", color = ApacheColors.textMuted, fontSize = 13.sp)
            }
            Switch(
                checked = briefingOn,
                onCheckedChange = {
                    briefingOn = it
                    settings.briefingEnabled = it
                    ApacheApp.get().alarms.scheduleBriefing()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = ApacheColors.accent)
            )
        }
        if (briefingOn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hora", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                fun shift(minutes: Long) {
                    val time = runCatching { LocalTime.parse(briefingTime) }.getOrDefault(LocalTime.of(8, 0)).plusMinutes(minutes)
                    briefingTime = "%02d:%02d".format(time.hour, time.minute)
                    settings.briefingTime = briefingTime
                    ApacheApp.get().alarms.scheduleBriefing()
                }
                IconButton(onClick = { shift(-15) }) { Icon(Icons.Filled.Remove, contentDescription = "Antes", tint = ApacheColors.accentLight) }
                Text(briefingTime, color = Color.White, fontSize = 20.sp)
                IconButton(onClick = { shift(15) }) { Icon(Icons.Filled.Add, contentDescription = "Después", tint = ApacheColors.accentLight) }
            }
            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        val briefing = Briefing.build(ApacheApp.get())
                        Notifications.showBriefing(context, briefing)
                        preview = briefing.title + "\n" + briefing.text
                        testing = false
                    }
                }
            ) { Text(if (testing) "Preparando…" else "Probar ahora", color = ApacheColors.accentLight) }
            preview?.let {
                Surface(color = ApacheColors.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diario: aviso por la noche.
        Text("Diario", color = ApacheColors.accent, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Preguntarme «¿qué tal el día?»", color = Color.White, fontSize = 15.sp)
                Text("Un aviso por la noche para apuntar tu día.", color = ApacheColors.textMuted, fontSize = 13.sp)
            }
            Switch(
                checked = diaryOn,
                onCheckedChange = {
                    diaryOn = it
                    settings.diaryEnabled = it
                    ApacheApp.get().alarms.scheduleDiary()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = ApacheColors.accent)
            )
        }
        if (diaryOn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hora", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                fun shiftDiary(minutes: Long) {
                    val time = runCatching { LocalTime.parse(diaryTime) }.getOrDefault(LocalTime.of(22, 0)).plusMinutes(minutes)
                    diaryTime = "%02d:%02d".format(time.hour, time.minute)
                    settings.diaryTime = diaryTime
                    ApacheApp.get().alarms.scheduleDiary()
                }
                IconButton(onClick = { shiftDiary(-15) }) { Icon(Icons.Filled.Remove, contentDescription = "Antes", tint = ApacheColors.accentLight) }
                Text(diaryTime, color = Color.White, fontSize = 20.sp)
                IconButton(onClick = { shiftDiary(15) }) { Icon(Icons.Filled.Add, contentDescription = "Después", tint = ApacheColors.accentLight) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Apache proactivo.
        Text("Apache proactivo", color = ApacheColors.accent, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Avisarme sin que pregunte", color = Color.White, fontSize = 15.sp)
                Text(
                    "Lluvia y tus planes, mañana sin alarma, la compra olvidada… Nunca de 23:00 a 8:00.",
                    color = ApacheColors.textMuted, fontSize = 13.sp
                )
            }
            Switch(
                checked = proactiveOn,
                onCheckedChange = {
                    proactiveOn = it
                    settings.proactiveEnabled = it
                    ApacheApp.get().alarms.scheduleProactive()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = ApacheColors.accent)
            )
        }
        if (proactiveOn) {
            TextButton(
                enabled = !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val notices = Proactive.collect(ApacheApp.get())
                        alerts = if (notices.isEmpty()) "Ahora mismo no hay nada que avisar." else notices.joinToString("\n\n") { "${it.title}\n${it.text}" }
                        checking = false
                    }
                }
            ) { Text(if (checking) "Mirando…" else "¿Hay algo que avisar ahora?", color = ApacheColors.accentLight) }
            alerts?.let {
                Surface(color = ApacheColors.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                settings.apiKey = apiKey
                settings.model = model
                settings.speakReplies = speak
                saved = true
                onSaved()
            },
            colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
        ) { Text("Guardar") }

        if (saved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("✓ Guardado", color = ApacheColors.accentLight, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Apache Móvil 0.2.0 · todo se guarda en este teléfono", color = ApacheColors.textFaint, fontSize = 12.sp)
    }
}
