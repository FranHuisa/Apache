package com.apache.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial de conversaciones.
 *
 * Cada mensaje guarda el "content" exacto de Gemini (role + parts en JSON),
 * así se le puede devolver el historial tal cual, incluidas las llamadas a
 * herramientas y sus firmas. `display_text` es lo que se ve en el chat; si es
 * null, el mensaje es interno (llamada o respuesta de herramienta).
 */
class ConversationStore(private val db: ApacheDatabase) {

    fun create(): Long {
        val now = ApacheDatabase.now()
        return db.writableDatabase.insert(
            "conversation", null,
            contentValues("created_at" to now, "updated_at" to now)
        )
    }

    fun exists(id: Long): Boolean =
        db.readableDatabase.rawQuery("SELECT id FROM conversation WHERE id = ?", arrayOf(id.toString()))
            .use { it.moveToFirst() }

    fun addMessage(
        conversationId: Long,
        content: JSONObject,
        displayText: String? = null,
        images: List<ChatImage> = emptyList()
    ) {
        val now = ApacheDatabase.now()
        val imagesJson = if (images.isEmpty()) null else JSONArray().apply {
            images.forEach {
                put(JSONObject().put("url", it.url).put("title", it.title).put("sourceUrl", it.sourceUrl ?: ""))
            }
        }.toString()

        db.writableDatabase.insert(
            "message", null,
            contentValues(
                "conversation_id" to conversationId,
                "role" to content.optString("role"),
                "content_json" to content.toString(),
                "display_text" to displayText,
                "images_json" to imagesJson,
                "created_at" to now
            )
        )

        // El título de la conversación es el primer mensaje del usuario.
        val title = if (displayText != null && content.optString("role") == "user") displayText.take(80) else null
        db.writableDatabase.execSQL(
            "UPDATE conversation SET updated_at = ?, title = COALESCE(title, ?) WHERE id = ?",
            arrayOf(now, title, conversationId)
        )
    }

    /** Contenidos para Gemini, en orden. */
    fun geminiContents(conversationId: Long): List<JSONObject> =
        db.readableDatabase.rawQuery(
            "SELECT content_json FROM message WHERE conversation_id = ? ORDER BY id",
            arrayOf(conversationId.toString())
        ).mapRows { JSONObject(it.string("content_json")) }

    /** Mensajes visibles, para pintar el chat. */
    fun visibleMessages(conversationId: Long): List<ChatMessage> =
        db.readableDatabase.rawQuery(
            "SELECT id, role, display_text, images_json FROM message " +
                "WHERE conversation_id = ? AND display_text IS NOT NULL ORDER BY id",
            arrayOf(conversationId.toString())
        ).mapRows { cursor ->
            ChatMessage(
                id = cursor.long("id"),
                isUser = cursor.string("role") == "user",
                text = cursor.string("display_text"),
                images = parseImages(cursor.stringOrNull("images_json"))
            )
        }

    fun list(limit: Int = 50): List<ConversationSummary> =
        db.readableDatabase.rawQuery(
            "SELECT id, title, updated_at FROM conversation WHERE title IS NOT NULL ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).mapRows {
            ConversationSummary(it.long("id"), it.stringOrNull("title") ?: "Conversación", it.string("updated_at"))
        }

    fun delete(conversationId: Long) {
        db.writableDatabase.delete("message", "conversation_id = ?", arrayOf(conversationId.toString()))
        db.writableDatabase.delete("conversation", "id = ?", arrayOf(conversationId.toString()))
    }

    private fun parseImages(json: String?): List<ChatImage> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatImage(
                url = item.getString("url"),
                title = item.optString("title"),
                sourceUrl = item.optString("sourceUrl").ifBlank { null }
            )
        }
    }
}
