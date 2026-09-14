package com.apache.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.NotificationDto
import com.apache.network.fetchUnreadNotifications
import com.apache.network.markNotificationRead
import com.apache.notifications.WindowsNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hace polling periódico al Core para las notificaciones sin leer del usuario
 * y las muestra como notificaciones nativas de Windows, manteniéndolas visibles
 * en la interfaz hasta que el usuario las descarta.
 *
 * El Desktop hace polling en vez de mantener una conexión persistente
 * (websocket, SSE...): para un asistente personal de un único usuario es más
 * que suficiente y muchísimo más simple.
 */
class NotificationsController(private val scope: CoroutineScope) {

    var pending by mutableStateOf<List<NotificationDto>>(emptyList())
        private set

    // Ids descartados localmente (el usuario pulsó la "✕") a la espera de que
    // el Core confirme el "marcar como leída".
    private val locallyDismissed = mutableSetOf<Long>()
    private val shown = mutableSetOf<Long>()

    fun dismiss(id: Long) {
        pending = pending.filter { it.id != id }
        locallyDismissed.add(id)

        scope.launch(Dispatchers.IO) {
            try {
                markNotificationRead(id)
            } catch (e: Exception) {
                locallyDismissed.remove(id)
            }
        }
    }

    /** Bucle de polling. Debe lanzarse una única vez, p. ej. desde un LaunchedEffect(Unit). */
    suspend fun startPolling() {
        while (true) {
            try {
                val notifications = withContext(Dispatchers.IO) { fetchUnreadNotifications() }

                locallyDismissed.retainAll(notifications.map { it.id }.toSet())
                val visible = notifications.filterNot { it.id in locallyDismissed }

                visible.filter { it.id !in shown }.forEach { notification ->
                    WindowsNotificationManager.show(notification.title, notification.message)
                    shown.add(notification.id)
                }

                pending = visible
            } catch (_: Exception) {
                // El Core puede no estar arrancado todavía, o haberse caído:
                // lo ignoramos y lo volvemos a intentar en el siguiente ciclo.
            }

            delay(15_000)
        }
    }
}
