package com.apache.model

// DTO de una notificación tal como la devuelve el Core (ver NotificationController).
data class NotificationDto(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: String
)
