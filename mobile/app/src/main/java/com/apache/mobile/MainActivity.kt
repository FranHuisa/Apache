package com.apache.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.apache.mobile.tools.DeviceLocation
import com.apache.mobile.ui.TasksScreen
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.apache.mobile.ui.ConversationOverlay
import com.apache.mobile.ui.HomeScreen
import com.apache.mobile.ui.MemoryScreen
import com.apache.mobile.ui.ScheduleScreen
import com.apache.mobile.ui.SettingsScreen

/** Única actividad de la app: toda la interfaz es Jetpack Compose. */
class MainActivity : ComponentActivity() {

    /** Lo que llega con "Compartir → Apache" antes de que el chat esté listo. */
    private var pendingShare by mutableStateOf<SharedContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingShare = readShare(intent)
        setContent {
            ApacheTheme {
                ApacheMobileApp(pendingShare, onShareConsumed = { pendingShare = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)?.let { pendingShare = it }
    }

    /** Texto, enlace o imágenes compartidos desde otra app. */
    @Suppress("DEPRECATION")
    private fun readShare(intent: Intent?): SharedContent? {
        if (intent == null) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> return null
        }
        if (text.isNullOrBlank() && uris.isEmpty()) return null
        return SharedContent(text, uris)
    }
}

internal class SharedContent(val text: String?, val uris: List<Uri>)

private enum class Section(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    CHAT("Chat", Icons.Filled.Chat),
    SCHEDULE("Agenda", Icons.Filled.CalendarMonth),
    MEMORY("Memoria", Icons.Filled.Psychology),
    SETTINGS("Ajustes", Icons.Filled.Settings)
}

@Composable
private fun ApacheMobileApp(share: SharedContent?, onShareConsumed: () -> Unit) {
    val chat: ChatViewModel = viewModel()
    val settings = ApacheApp.get().settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Sin API key, se empieza en Ajustes.
    var section by rememberSaveable { mutableStateOf(if (settings.isConfigured) Section.HOME else Section.SETTINGS) }
    val snackbar = remember { SnackbarHostState() }

    // Permisos al empezar: avisos (Android 13+) y ubicación (para el tiempo y "cerca de mí").
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true || granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            scope.launch {
                DeviceLocation.current()
                chat.locationVersion++
            }
        }
    }
    LaunchedEffect(Unit) {
        val wanted = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray()) else DeviceLocation.current()
        chat.dailySummaryIfNeeded()
    }

    // Compartir → Apache: se abre el chat con lo compartido.
    LaunchedEffect(share) {
        share?.let {
            chat.receiveShare(it.text, it.uris)
            section = Section.CHAT
            onShareConsumed()
        }
    }

    // Micrófono: pide permiso la primera vez y luego empieza/para de escuchar.
    var wantsConversation by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            chat.notice = "Sin permiso del micrófono no puedo escucharte."
        } else if (wantsConversation) {
            chat.startConversation()
        } else {
            section = Section.CHAT
            chat.toggleListening()
        }
        wantsConversation = false
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

    // Conversación manos libres (como una llamada con Apache).
    val onConversation: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            chat.startConversation()
        } else {
            wantsConversation = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    BackHandler(enabled = chat.conversationMode) { chat.endConversation() }

    // Avisos cortos del chat (imagen guardada, errores de voz...).
    LaunchedEffect(chat.notice) {
        chat.notice?.let {
            snackbar.showSnackbar(it)
            chat.notice = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        onMic = onMic,
                        onConversation = onConversation
                    )
                    Section.CHAT -> ChatScreen(chat, onMic, onConversation)
                    Section.SCHEDULE -> PlannerScreen(chat)
                    Section.MEMORY -> MemoryScreen(chat, onOpenChat = { section = Section.CHAT })
                    Section.SETTINGS -> SettingsScreen(onSaved = {
                        chat.dailySummaryIfNeeded()
                        section = Section.HOME
                    })
                }
            }
        }
    }

    // Modo conversación: encima de todo.
    AnimatedVisibility(visible = chat.conversationMode, enter = fadeIn(), exit = fadeOut()) {
        ConversationOverlay(chat)
    }
    }
}

/** Agenda: el horario del día y las listas, con un selector arriba. */
@Composable
private fun PlannerScreen(chat: ChatViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp).fillMaxWidth()
                .clip(RoundedCornerShape(50)).background(ApacheColors.surface).padding(4.dp)
        ) {
            listOf("Horario", "Listas").forEachIndexed { index, label ->
                val selected = tab == index
                val bg by animateColorAsState(if (selected) ApacheColors.accent else Color.Transparent, label = "planner$index")
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg)
                        .clickable { tab = index }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) Color.Black else ApacheColors.textMuted, fontSize = 14.sp)
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (tab == 0) ScheduleScreen(chat) else {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    TasksScreen(chat)
                }
            }
        }
    }
}
