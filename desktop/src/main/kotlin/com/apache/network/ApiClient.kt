package com.apache.network

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.time.Duration

/**
 * Dirección base del Apache Core.
 *
 * Centralizada aquí para no repetir "http://localhost:8080" en cada función
 * de red y para poder cambiarla en un único sitio (p. ej. si algún día se
 * hace configurable).
 */
const val CORE_BASE_URL = "http://localhost:8080"

/**
 * Cliente HTTP compartido por todas las llamadas al Core.
 *
 * El tiempo de lectura por defecto de OkHttp es 10 s, y un turno de Apache
 * puede tardar bastante más (varias herramientas seguidas, búsqueda en
 * internet, imágenes adjuntas...). Con 10 s el Desktop daba el turno por
 * fallido aunque el Core lo terminara bien.
 */
val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(10))
    .readTimeout(Duration.ofSeconds(150))
    .writeTimeout(Duration.ofSeconds(60))
    .build()

/** Conversor JSON compartido. */
val objectMapper = jacksonObjectMapper()

/** Tipo de contenido que enviamos al Core. */
val jsonMediaType = "application/json".toMediaType()
