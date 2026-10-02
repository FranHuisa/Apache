package com.apache.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apache.mobile.ui.ApacheColors
import com.apache.mobile.ui.ApacheTheme
import com.apache.mobile.ui.ChatScreen
import com.apache.mobile.ui.ChatViewModel
import com.apache.mobile.ui.MemoryScreen
import com.apache.mobile.ui.ScheduleScreen
import com.apache.mobile.ui.SettingsScreen

/** Única actividad de la app: toda la interfaz es Jetpack Compose. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApacheTheme {
                ApacheMobileApp()
            }
        }
    }
}

private enum class Section(val label: String, val icon: String) {
    CHAT("Chat", "💬"),
    SCHEDULE("Horario", "📅"),
    MEMORY("Memoria", "🧠"),
    SETTINGS("Ajustes", "⚙️")
}

@Composable
private fun ApacheMobileApp() {
    val chat: ChatViewModel = viewModel()
    val settings = ApacheApp.get().settings

    // Sin API key, se empieza en Ajustes.
    var section by rememberSaveable { mutableStateOf(if (settings.isConfigured) Section.CHAT else Section.SETTINGS) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: permiso para los avisos de recordatorios y del horario.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        chat.dailySummaryIfNeeded()
    }

    // Avisos cortos del chat (imagen guardada, errores de voz...).
    LaunchedEffect(chat.notice) {
        chat.notice?.let {
            snackbar.showSnackbar(it)
            chat.notice = null
        }
    }

    Scaffold(
        containerColor = ApacheColors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = ApacheColors.surface) {
                Section.values().forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = { Text(item.icon, fontSize = 20.sp) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = ApacheColors.accent,
                            indicatorColor = ApacheColors.card
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (section) {
                Section.CHAT -> ChatScreen(chat)
                Section.SCHEDULE -> ScheduleScreen(chat)
                Section.MEMORY -> MemoryScreen(chat, onOpenChat = { section = Section.CHAT })
                Section.SETTINGS -> SettingsScreen(onSaved = {
                    chat.dailySummaryIfNeeded()
                })
            }
        }
    }
}
