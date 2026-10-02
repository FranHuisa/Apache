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
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

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
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_SPEAK = "speak_replies"
        private const val KEY_CONVERSATION = "conversation_id"
        private const val KEY_SUMMARY = "last_summary_date"
    }
}
