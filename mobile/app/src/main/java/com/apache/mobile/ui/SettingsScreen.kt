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
import androidx.compose.material3.Button
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

/** Ajustes: API key de Gemini, modelo y voz. */
@Composable
fun SettingsScreen(onSaved: () -> Unit) {
    val settings = ApacheApp.get().settings
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var speak by remember { mutableStateOf(settings.speakReplies) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

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
        Text("Apache Móvil 0.1.0 · todo se guarda en este teléfono", color = ApacheColors.textFaint, fontSize = 12.sp)
    }
}
