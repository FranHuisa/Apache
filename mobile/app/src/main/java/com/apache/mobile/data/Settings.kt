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
            return text.substringAfterLast('=').substringAfterLast(": ")
                .trim().trim('"', '\'', '`').trim()
        }

        /** Las claves de Gemini empiezan por "AIza" y tienen 39 caracteres. */
        fun looksLikeGeminiKey(key: String): Boolean = key.startsWith("AIza") && key.length in 35..45

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_SPEAK = "speak_replies"
        private const val KEY_CONVERSATION = "conversation_id"
        private const val KEY_SUMMARY = "last_summary_date"
    }
}
