package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.service.NotificationService
import org.springframework.stereotype.Component

/** Tool de solo lectura que consulta las notificaciones sin leer del usuario. */
@Component
class ListNotificationsTool(
    private val notificationService: NotificationService
) : Tool {

    override val name = "listNotifications"

    override val description =
        "Consulta las notificaciones pendientes de leer del usuario (por ejemplo, recordatorios " +
            "que ya se han disparado). Útil si el usuario pregunta '¿tengo notificaciones?' o '¿algo pendiente?'."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any?>()
    )

    override fun execute(args: Map<String, Any?>): String {
        val notifications = notificationService.listNotifications(
            ApacheDefaults.DEFAULT_USER_ID,
            onlyUnread = true
        )

        if (notifications.isEmpty()) {
            return "No hay notificaciones pendientes."
        }

        return notifications.joinToString("\n") { "#${it.id} · ${it.title}: ${it.message}" }
    }
}
