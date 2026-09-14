package com.apache.util

// Palabra de activación que Apache reconoce en el modo escucha.
val wakeWordRegex = Regex("(?i)\\bapache\\b")

// Órdenes locales: no se envían al Agent porque controlan la propia escucha.
val stopListeningRegex = Regex(
    "(?i)^\\s*(?:apache[\\s,.:;-]*)?(?:corta|corto|apaga)(?:\\s+(?:el\\s+)?(?:modo\\s+)?escucha)?\\s*[.!]?\\s*$"
)
