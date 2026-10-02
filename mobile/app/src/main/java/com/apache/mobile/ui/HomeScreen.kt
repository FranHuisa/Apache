package com.apache.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.ApacheApp
import com.apache.mobile.data.CalendarEvent
import com.apache.mobile.data.TaskListSummary
import com.apache.mobile.tools.CurrentWeather
import com.apache.mobile.tools.WeatherService
import com.apache.mobile.ui.components.ApacheOrb
import com.apache.mobile.ui.components.MicButton
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val TODAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))

/**
 * Inicio: saludo, tiempo de tu ciudad, lo próximo del día y accesos rápidos.
 * Las tarjetas aparecen con una pequeña animación escalonada.
 */
@Composable
fun HomeScreen(chat: ChatViewModel, onOpenChat: () -> Unit, onOpenSchedule: () -> Unit, onMic: () -> Unit) {
    val app = ApacheApp.get()

    var name by remember { mutableStateOf<String?>(null) }
    var city by remember { mutableStateOf<String?>(null) }
    var weather by remember { mutableStateOf<CurrentWeather?>(null) }
    var upcoming by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var reminders by remember { mutableStateOf(0) }
    var lists by remember { mutableStateOf<List<TaskListSummary>>(emptyList()) }
    var weatherLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) {
            val now = LocalDateTime.now()
            HomeData(
                name = app.memory.findByKey("nombre")?.value,
                city = (app.memory.findByKey("ciudad") ?: app.memory.findByKey("ubicación")
                    ?: app.memory.findByKey("ubicacion"))?.value,
                upcoming = app.events.between(now.minusHours(1), LocalDate.now().atTime(23, 59))
                    .filter { it.status == "confirmed" }.take(3),
                reminders = app.reminders.pending().size,
                lists = app.tasks.lists().filter { it.pending > 0 }
            )
        }
        name = data.name
        city = data.city
        upcoming = data.upcoming
        reminders = data.reminders
        lists = data.lists
        // Con ciudad guardada, esa; si no, donde está el móvil.
        weather = WeatherService.current(data.city)
        weatherLoading = false
    }

    val greeting = when (LocalTime.now().hour) {
        in 6..13 -> "Buenos días"
        in 14..20 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    fun ask(text: String) {
        chat.send(text)
        onOpenChat()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ApacheOrb(size = 52.dp, alive = chat.isLoading)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    greeting + (name?.let { ", $it" } ?: ""),
                    color = Color.White, fontSize = 24.sp
                )
                Text(
                    LocalDate.now().format(TODAY).replaceFirstChar { it.titlecase() },
                    color = ApacheColors.textMuted, fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tiempo.
        Appear(delay = 0) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF16402A), Color(0xFF0F2E3A))))
                    .clickable { ask(if (city != null) "¿Qué tiempo hará estos días?" else "¿Qué tiempo hace?") }
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val current = weather
                    if (current != null) {
                        Text(current.emoji, fontSize = 40.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("${current.temperature}°", color = Color.White, fontSize = 34.sp)
                            Text("${current.description} · ${current.place}", color = ApacheColors.accentSoft, fontSize = 13.sp)
                        }
                    } else {
                        Icon(Icons.Filled.WbSunny, contentDescription = null, tint = ApacheColors.accentLight, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            if (weatherLoading) "Mirando el tiempo…"
                            else "Activa la ubicación o dile a Apache «vivo en …» y verás aquí el tiempo.",
                            color = ApacheColors.accentSoft, fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lo próximo.
        Appear(delay = 80) {
            Surface(
                color = ApacheColors.card,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { onOpenSchedule() }
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = ApacheColors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hoy", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (reminders > 0) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = ApacheColors.accentLight, modifier = Modifier.size(16.dp))
                            Text(" $reminders", color = ApacheColors.accentLight, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (lists.isNotEmpty()) {
                        Text(
                            "📝 Pendiente: " + lists.joinToString { "${it.name} (${it.pending})" },
                            color = ApacheColors.accentSoft, fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    if (upcoming.isEmpty()) {
                        Text("Nada más por hoy. Toca para organizar el día.", color = ApacheColors.textMuted, fontSize = 14.sp)
                    } else {
                        upcoming.forEach { event ->
                            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(ApacheColors.accent))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(event.startAt.format(HOUR), color = ApacheColors.accentSoft, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(event.title, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text("Pídele a Apache", color = ApacheColors.textMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))

        // Accesos rápidos.
        val actions = listOf(
            QuickAction("¿Qué tengo hoy?", Icons.Filled.CalendarMonth, "¿Qué tengo hoy?"),
            QuickAction("Organiza mi día", Icons.Filled.AutoAwesome, "Organízame el día de hoy"),
            QuickAction("Noticias de hoy", Icons.Filled.Newspaper, "¿Cuáles son las noticias de hoy?"),
            QuickAction("¿Qué tiempo hace?", Icons.Filled.WbSunny, "¿Qué tiempo hace hoy y mañana?"),
            QuickAction("Mi resumen", Icons.Filled.Today, "Dame mi resumen del día"),
            QuickAction("Lista de la compra", Icons.Filled.ShoppingCart, "¿Qué tengo en la lista de la compra?"),
            QuickAction("Pon música", Icons.Filled.MusicNote, "Pon música para concentrarme"),
            QuickAction("Enséñame algo", Icons.Filled.Image, "Enséñame una foto bonita de un paisaje")
        )
        actions.chunked(2).forEachIndexed { rowIndex, row ->
            Appear(delay = 160 + rowIndex * 80) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { action ->
                        Surface(
                            color = ApacheColors.cardAlt,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(18.dp))
                                .border(1.dp, Color(0x221DB954), RoundedCornerShape(18.dp))
                                .clickable { ask(action.prompt) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(action.icon, contentDescription = null, tint = ApacheColors.accent)
                                Text(action.label, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hablar directamente.
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(listening = chat.isListening, enabled = !chat.isLoading, onClick = onMic, size = 64.dp)
            Text("Toca y habla", color = ApacheColors.textFaint, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private data class HomeData(
    val name: String?,
    val city: String?,
    val upcoming: List<CalendarEvent>,
    val reminders: Int,
    val lists: List<TaskListSummary>
)

private data class QuickAction(val label: String, val icon: ImageVector, val prompt: String)

/** Aparece deslizándose desde abajo, con un pequeño retraso. */
@Composable
fun Appear(delay: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(androidx.compose.animation.core.tween(400, delayMillis = delay)) +
            slideInVertically(androidx.compose.animation.core.tween(400, delayMillis = delay)) { it / 4 }
    ) {
        content()
    }
}
