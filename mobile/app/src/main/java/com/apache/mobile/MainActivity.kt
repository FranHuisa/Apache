package com.apache.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apache.mobile.ui.ApacheColors
import com.apache.mobile.ui.ApacheTheme
import com.apache.mobile.ui.ChatScreen
import com.apache.mobile.ui.ChatViewModel
import com.apache.mobile.ui.HomeScreen
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

private enum class Section(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    CHAT("Chat", Icons.Filled.Chat),
    SCHEDULE("Horario", Icons.Filled.CalendarMonth),
    MEMORY("Memoria", Icons.Filled.Psychology),
    SETTINGS("Ajustes", Icons.Filled.Settings)
}

@Composable
private fun ApacheMobileApp() {
    val chat: ChatViewModel = viewModel()
    val settings = ApacheApp.get().settings
    val context = LocalContext.current

    // Sin API key, se empieza en Ajustes.
    var section by rememberSaveable { mutableStateOf(if (settings.isConfigured) Section.HOME else Section.SETTINGS) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: permiso para los avisos de recordatorios y del horario.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        chat.dailySummaryIfNeeded()
    }

    // Micrófono: pide permiso la primera vez y luego empieza/para de escuchar.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            section = Section.CHAT
            chat.toggleListening()
        } else {
            chat.notice = "Sin permiso del micrófono no puedo escucharte."
        }
    }
    val onMic: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (!chat.isListening) section = Section.CHAT
            chat.toggleListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
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
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = ApacheColors.accent,
                            indicatorColor = ApacheColors.accent,
                            unselectedIconColor = ApacheColors.textMuted,
                            unselectedTextColor = ApacheColors.textMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            Crossfade(targetState = section, label = "section") { current ->
                when (current) {
                    Section.HOME -> HomeScreen(
                        chat = chat,
                        onOpenChat = { section = Section.CHAT },
                        onOpenSchedule = { section = Section.SCHEDULE },
                        onMic = onMic
                    )
                    Section.CHAT -> ChatScreen(chat, onMic)
                    Section.SCHEDULE -> ScheduleScreen(chat)
                    Section.MEMORY -> MemoryScreen(chat, onOpenChat = { section = Section.CHAT })
                    Section.SETTINGS -> SettingsScreen(onSaved = {
                        chat.dailySummaryIfNeeded()
                        section = Section.HOME
                    })
                }
            }
        }
    }
}
