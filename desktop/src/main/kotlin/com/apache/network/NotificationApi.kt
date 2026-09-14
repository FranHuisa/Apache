package com.apache.network

import com.apache.model.NotificationDto
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Consulta al Core las notificaciones sin leer del usuario. */
fun fetchUnreadNotifications(): List<NotificationDto> {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/notifications?onlyUnread=true")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Error del Core: ${response.code}")
        }

        return objectMapper.readValue(bodyText)
    }
}

/** Marca una notificación como leída en el Core, para que deje de aparecer en el siguiente polling. */
fun markNotificationRead(id: Long) {
    val request = Request.Builder()
        .url("$CORE_BASE_URL/api/notifications/$id/read")
        .post("".toRequestBody(jsonMediaType))
        .build()

    httpClient.newCall(request).execute().close()
}
