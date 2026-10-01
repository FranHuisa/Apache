package com.apache.session

import java.io.File
import java.time.LocalDate
import java.util.Properties

/**
 * Preferencias y estado del Desktop que deben sobrevivir a un reinicio.
 *
 * Se guardan en un archivo de texto plano dentro de la carpeta de
 * configuración del usuario: ~/.apache/session.properties
 *
 *  - conversationId: última conversación, para continuarla al reabrir Apache
 *    (sin esto Apache empezaba una conversación nueva aunque en MySQL ya
 *    existiera el historial completo).
 *  - voiceMuted: si la voz de Apache estaba silenciada.
 *  - lastSummaryDate: último día en que se mostró el resumen del día.
 *
 * Cada escritura lee el archivo, cambia solo su clave y lo vuelve a guardar,
 * para no borrar las demás.
 */
object SessionStore {

    private val sessionDir = File(System.getProperty("user.home"), ".apache")
    private val sessionFile = File(sessionDir, "session.properties")

    private const val KEY_CONVERSATION = "conversationId"
    private const val KEY_MUTED = "voiceMuted"
    private const val KEY_SUMMARY_DATE = "lastSummaryDate"

    // --- Conversación ---

    /** Lee el conversationId guardado, o null si no hay ninguno (primer arranque). */
    fun loadConversationId(): Long? = read(KEY_CONVERSATION)?.toLongOrNull()

    /** Guarda el conversationId actual para poder recuperarlo en el próximo arranque. */
    fun saveConversationId(conversationId: Long?) {
        if (conversationId == null) return
        write(KEY_CONVERSATION, conversationId.toString())
    }

    /** Olvida la conversación actual: el próximo mensaje empezará una nueva. */
    fun clearConversationId() = write(KEY_CONVERSATION, null)

    // --- Voz ---

    fun loadVoiceMuted(): Boolean = read(KEY_MUTED)?.toBooleanStrictOrNull() ?: false

    fun saveVoiceMuted(muted: Boolean) = write(KEY_MUTED, muted.toString())

    // --- Resumen del día ---

    /** true si hoy todavía no se ha mostrado el resumen del día. */
    fun shouldShowDailySummary(): Boolean = read(KEY_SUMMARY_DATE) != LocalDate.now().toString()

    fun markDailySummaryShown() = write(KEY_SUMMARY_DATE, LocalDate.now().toString())

    // --- Lectura / escritura ---

    private fun load(): Properties {
        val properties = Properties()
        try {
            if (sessionFile.exists()) {
                sessionFile.inputStream().use { properties.load(it) }
            }
        } catch (_: Exception) {
            // Archivo dañado o ilegible: empezamos con valores por defecto.
        }
        return properties
    }

    private fun read(key: String): String? = load().getProperty(key)

    /** Cambia una clave (o la borra si [value] es null) conservando las demás. */
    @Synchronized
    private fun write(key: String, value: String?) {
        try {
            if (!sessionDir.exists()) sessionDir.mkdirs()

            val properties = load()
            if (value == null) properties.remove(key) else properties.setProperty(key, value)

            sessionFile.outputStream().use {
                properties.store(it, "Apache - estado del Desktop")
            }
        } catch (_: Exception) {
            // No es crítico: como mucho se pierde la preferencia en el próximo arranque.
        }
    }
}
