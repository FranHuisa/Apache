package com.apache.mobile.tools

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Herramienta que Gemini puede usar (function calling). Igual que en el Core
 * de escritorio: una declaración (nombre, descripción, parámetros) y una
 * implementación ([execute]).
 */
interface Tool {
    val name: String
    val description: String

    /** Esquema JSON de los parámetros (ver [Schema]). */
    val parameters: JSONObject

    /**
     * true si el resultado ya es la respuesta para el usuario (abrir una app,
     * poner una alarma...) y no hace falta otra vuelta a Gemini.
     */
    val direct: Boolean
        get() = false

    /** Se ejecuta fuera del hilo principal. Devuelve texto para Gemini. */
    suspend fun execute(args: JSONObject): String

    fun declaration(): JSONObject =
        JSONObject().put("name", name).put("description", description).put("parameters", parameters)
}

/** Ayudas para escribir los esquemas de parámetros sin JSON a mano. */
object Schema {
    fun obj(vararg properties: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
        val props = JSONObject()
        properties.forEach { (key, value) -> props.put(key, value) }
        val schema = JSONObject().put("type", "object").put("properties", props)
        if (required.isNotEmpty()) schema.put("required", JSONArray(required))
        return schema
    }

    fun string(description: String, enum: List<String>? = null): JSONObject =
        JSONObject().put("type", "string").put("description", description).also { schema ->
            enum?.let { schema.put("enum", JSONArray(it)) }
        }

    fun integer(description: String) = JSONObject().put("type", "integer").put("description", description)
    fun number(description: String) = JSONObject().put("type", "number").put("description", description)
    fun boolean(description: String) = JSONObject().put("type", "boolean").put("description", description)

    fun array(description: String, items: JSONObject) =
        JSONObject().put("type", "array").put("description", description).put("items", items)
}

/** Lectura tolerante de argumentos (Gemini a veces manda números como texto). */
internal fun JSONObject.str(key: String): String? = optString(key, "").trim().ifBlank { null }

internal fun JSONObject.int(key: String): Int? =
    if (!has(key)) null else (opt(key) as? Number)?.toInt() ?: optString(key).trim().toIntOrNull()

internal fun JSONObject.long(key: String): Long? =
    if (!has(key)) null else (opt(key) as? Number)?.toLong() ?: optString(key).trim().toLongOrNull()

/** "2026-09-15T18:30" o "2026-09-15" (medianoche). */
internal fun parseDateTime(value: String?): LocalDateTime? {
    val text = value?.trim()
    if (text.isNullOrEmpty()) return null
    return runCatching { LocalDateTime.parse(text) }.getOrNull()
        ?: runCatching { LocalDate.parse(text).atStartOfDay() }.getOrNull()
}

/** "9:30", "09:30" o "09:30:00". */
internal fun parseTime(value: String?): LocalTime? {
    val text = value?.trim()
    if (text.isNullOrEmpty()) return null
    val normalized = if (text.length == 4 && text[1] == ':') "0$text" else text
    return runCatching { LocalTime.parse(normalized) }.getOrNull()
}

/** HTTP compartido por las herramientas. Bloqueante: llamar desde Dispatchers.IO. */
internal object Http {
    const val USER_AGENT = "ApacheMobile/0.1 (asistente personal)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (key, value) -> builder.header(key, value) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    fun getJson(url: String): JSONObject = JSONObject(getText(url))

    /** Descarga binaria: (bytes, tipo MIME). */
    fun getBytes(url: String, maxBytes: Int = 15_000_000): Pair<ByteArray, String> {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: throw IllegalStateException("Respuesta vacía")
            if (bytes.size > maxBytes) throw IllegalStateException("Archivo demasiado grande")
            val type = response.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
            return bytes to type
        }
    }
}
