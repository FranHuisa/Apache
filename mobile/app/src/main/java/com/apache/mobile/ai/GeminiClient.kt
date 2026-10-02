package com.apache.mobile.ai

import com.apache.mobile.data.Settings
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Error de Gemini con un mensaje ya preparado para enseñar al usuario. */
class GeminiException(message: String) : Exception(message)

/**
 * Cliente de la API REST de Gemini (`generateContent`), llamado directamente
 * desde el móvil con la API key de los Ajustes.
 *
 * Reintenta cuando Gemini está saturado (503/429) o tarda demasiado, igual
 * que el Core de escritorio.
 */
class GeminiClient(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Envía una petición completa y devuelve el `content` del primer candidato
     * (role "model" + parts).
     */
    suspend fun generate(
        contents: JSONArray,
        systemInstruction: String? = null,
        functionDeclarations: JSONArray? = null,
        extraTools: JSONArray? = null,
        temperature: Double? = null
    ): JSONObject {
        val body = JSONObject().put("contents", contents)

        systemInstruction?.let {
            body.put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it))))
        }

        val tools = JSONArray()
        if (functionDeclarations != null && functionDeclarations.length() > 0) {
            tools.put(JSONObject().put("functionDeclarations", functionDeclarations))
        }
        if (extraTools != null) {
            for (i in 0 until extraTools.length()) tools.put(extraTools.get(i))
        }
        if (tools.length() > 0) body.put("tools", tools)

        temperature?.let { body.put("generationConfig", JSONObject().put("temperature", it)) }

        val response = post(body)
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw GeminiException("Gemini no ha devuelto ninguna respuesta (puede que la haya bloqueado).")

        return candidate.optJSONObject("content")
            ?: JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "")))
    }

    /** Devuelve la respuesta completa (para leer, por ejemplo, groundingMetadata). */
    suspend fun generateRaw(body: JSONObject): JSONObject = post(body)

    private suspend fun post(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) {
            throw GeminiException("Falta la API key de Gemini. Ponla en Ajustes.")
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${settings.model}:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val waits = listOf(1_000L, 3_000L, 6_000L)
        var timeoutRetried = false
        var attempt = 0

        while (true) {
            val response = try {
                http.newCall(request).execute()
            } catch (e: InterruptedIOException) {
                if (timeoutRetried) throw GeminiException("Gemini está tardando demasiado. Inténtalo en un momento.")
                timeoutRetried = true
                continue
            } catch (e: java.io.IOException) {
                throw GeminiException("No hay conexión con Gemini. Revisa el WiFi o los datos.")
            }

            val code = response.code
            val text = response.body?.string().orEmpty()
            response.close()

            if (code == 503 || code == 429) {
                if (attempt >= waits.size) throw GeminiException("Gemini está saturado ahora mismo. Inténtalo en unos segundos.")
                delay(waits[attempt++])
                continue
            }

            if (code == 400 && "API key" in text) throw GeminiException("La API key de Gemini no es válida. Revísala en Ajustes.")
            if (code == 404) throw GeminiException("El modelo «${settings.model}» no existe. Cámbialo en Ajustes.")
            if (code !in 200..299) throw GeminiException("Gemini respondió con un error ($code).")

            return@withContext JSONObject(text)
        }
        @Suppress("UNREACHABLE_CODE")
        JSONObject()
    }
}
