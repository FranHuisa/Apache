package com.apache.mobile.data

/**
 * Memoria permanente: datos del usuario que Apache tiene en cuenta en todas
 * las conversaciones (nombre, gustos, ciudad, rutinas...).
 */
class MemoryStore(private val db: ApacheDatabase) {

    fun all(): List<MemoryFact> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM memory ORDER BY importance DESC, updated_at DESC", null
        ).mapRows {
            MemoryFact(
                id = it.long("id"),
                type = it.string("type"),
                key = it.string("memory_key"),
                value = it.string("memory_value"),
                importance = it.double("importance")
            )
        }

    /** Guarda un dato; si ya existe uno con la misma clave (sin distinguir mayúsculas), lo actualiza. */
    fun remember(key: String, value: String, type: String = "otro", importance: Double = 0.5): Pair<MemoryFact, Boolean> {
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        require(cleanKey.isNotBlank() && cleanValue.isNotBlank()) { "Falta el nombre o el contenido del dato." }

        val cleanType = if (type in TYPES) type else "otro"
        val existing = findByKey(cleanKey)

        val values = contentValues(
            "type" to cleanType,
            "memory_key" to cleanKey,
            "memory_value" to cleanValue,
            "importance" to importance.coerceIn(0.0, 1.0),
            "updated_at" to ApacheDatabase.now()
        )

        return if (existing != null) {
            db.writableDatabase.update("memory", values, "id = ?", arrayOf(existing.id.toString()))
            existing.copy(key = cleanKey, value = cleanValue, type = cleanType) to false
        } else {
            val id = db.writableDatabase.insert("memory", null, values)
            MemoryFact(id, cleanType, cleanKey, cleanValue, importance) to true
        }
    }

    fun update(id: Long, key: String, value: String, type: String) {
        db.writableDatabase.update(
            "memory",
            contentValues(
                "memory_key" to key.trim(),
                "memory_value" to value.trim(),
                "type" to (if (type in TYPES) type else "otro"),
                "updated_at" to ApacheDatabase.now()
            ),
            "id = ?", arrayOf(id.toString())
        )
    }

    fun delete(id: Long) {
        db.writableDatabase.delete("memory", "id = ?", arrayOf(id.toString()))
    }

    fun findByKey(key: String): MemoryFact? = all().firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }

    /** Texto para la instrucción de sistema (los 60 datos más importantes). */
    fun contextForPrompt(): String = all().take(60).joinToString("\n") { "- ${it.key}: ${it.value}" }

    companion object {
        val TYPES = listOf("personal", "preferencia", "rutina", "trabajo", "salud", "otro")

        fun typeLabel(type: String): String = when (type) {
            "personal" -> "Sobre ti"
            "preferencia" -> "Gustos y preferencias"
            "rutina" -> "Rutinas"
            "trabajo" -> "Trabajo y estudios"
            "salud" -> "Salud"
            else -> "Otros"
        }
    }
}
