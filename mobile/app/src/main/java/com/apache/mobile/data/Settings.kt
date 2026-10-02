package com.apache.mobile.data

import android.content.Context

/**
 * Ajustes de Apache Móvil, guardados en las preferencias privadas de la app
 * (solo accesibles por Apache).
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("apache_settings", Context.MODE_PRIVATE)

    /** API key de Gemini (https://aistudio.google.com/apikey). Sin ella Apache no puede responder. */
    var apiKey: String
        get() = cleanApiKey(prefs.getString(KEY_API, "").orEmpty())
        set(value) = prefs.edit().putString(KEY_API, cleanApiKey(value)).apply()

    /** Modelo de Gemini. Por defecto, el mismo que usa Apache de escritorio. */
    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** Leer en voz alta las respuestas a lo que se pregunta por voz. */
    var speakReplies: Boolean
        get() = prefs.getBoolean(KEY_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAK, value).apply()

    /** Última conversación, para continuarla al reabrir la app. */
    var conversationId: Long?
        get() = prefs.getLong(KEY_CONVERSATION, -1L).takeIf { it > 0 }
        set(value) = prefs.edit().putLong(KEY_CONVERSATION, value ?: -1L).apply()

    /** Último día en que se mostró el resumen del día (yyyy-MM-dd). */
    var lastSummaryDate: String
        get() = prefs.getString(KEY_SUMMARY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SUMMARY, value).apply()

    /** Resumen de buenos días: activado y hora ("08:00"). */
    var briefingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BRIEFING_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_BRIEFING_ON, value).apply()

    var briefingTime: String
        get() = prefs.getString(KEY_BRIEFING_TIME, "08:00").orEmpty().ifBlank { "08:00" }
        set(value) = prefs.edit().putString(KEY_BRIEFING_TIME, value).apply()

    /**
     * Última ubicación conocida del móvil (se guarda cada vez que la app la
     * consigue). La usa el resumen de buenos días, que corre en segundo plano.
     */
    var lastLocation: SavedLocation?
        get() {
            val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
            val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
            return SavedLocation(lat, lon, prefs.getString(KEY_PLACE, null))
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_LAT); remove(KEY_LON); remove(KEY_PLACE)
                } else {
                    putString(KEY_LAT, value.latitude.toString())
                    putString(KEY_LON, value.longitude.toString())
                    putString(KEY_PLACE, value.place)
                }
            }.apply()
        }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    companion object {
        /**
         * Limpia lo que se suele pegar de más junto a la clave: espacios, saltos
         * de línea, comillas o el texto "GEMINI_API_KEY=" / "api-key:".
         * Si dentro hay algo con forma de clave de Gemini (AIza...), se queda con eso.
         */
        fun cleanApiKey(raw: String): String {
            val text = raw.trim()
            Regex("AIza[0-9A-Za-z_\\-]{30,}").find(text)?.let { return it.value }
            Regex("AQ\\.[0-9A-Za-z_\\-.]{20,}").find(text)?.let { return it.value }
            return text.substringAfterLast('=').substringAfterLast(": ")
                .trim().trim('"', '\'', '`').trim()
        }

        /** Claves de Gemini: las clásicas "AIza…" (39 caracteres) y las nuevas "AQ.…". */
        fun looksLikeGeminiKey(key: String): Boolean =
            (key.startsWith("AIza") && key.length in 35..45) || (key.startsWith("AQ.") && key.length >= 30)

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_SPEAK = "speak_replies"
        private const val KEY_CONVERSATION = "conversation_id"
        private const val KEY_SUMMARY = "last_summary_date"
        private const val KEY_BRIEFING_ON = "briefing_enabled"
        private const val KEY_BRIEFING_TIME = "briefing_time"
        private const val KEY_LAT = "last_latitude"
        private const val KEY_LON = "last_longitude"
        private const val KEY_PLACE = "last_place"
    }
}

/** Ubicación guardada: coordenadas y, si se sabe, el nombre del sitio. */
data class SavedLocation(val latitude: Double, val longitude: Double, val place: String?)
