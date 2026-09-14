package com.apache.session

/**
 * Persiste el `conversationId` de la última conversación en disco.
 *
 * Sin esto, cada vez que se cierra y se vuelve a abrir el Desktop se pierde el `conversationId`
 * (solo vivía en memoria de Compose), y Apache empezaba una conversación nueva aunque en MySQL ya
 * existiera el historial completo.
 *
 * Se guarda en un archivo de texto plano dentro de la carpeta de configuración del usuario:
 * ~/.apache/session.properties
 */
object SessionStore {

    private val sessionDir = java.io.File(System.getProperty("user.home"), ".apache")
    private val sessionFile = java.io.File(sessionDir, "session.properties")

    /** Lee el conversationId guardado, o null si no hay ninguno (primer arranque). */
    fun loadConversationId(): Long? {
        return try {
            if (!sessionFile.exists()) return null

            val properties = java.util.Properties()
            sessionFile.inputStream().use { properties.load(it) }

            properties.getProperty("conversationId")?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /** Guarda el conversationId actual para poder recuperarlo en el próximo arranque. */
    fun saveConversationId(conversationId: Long?) {
        if (conversationId == null) return

        try {
            if (!sessionDir.exists()) {
                sessionDir.mkdirs()
            }

            val properties = java.util.Properties()
            properties.setProperty("conversationId", conversationId.toString())

            sessionFile.outputStream().use {
                properties.store(it, "Apache - sesión de conversación actual")
            }
        } catch (e: Exception) {}
    }
}
