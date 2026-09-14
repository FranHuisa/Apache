package com.apache.network

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

/**
 * Dirección base del Apache Core.
 *
 * Centralizada aquí para no repetir "http://localhost:8080" en cada función
 * de red y para poder cambiarla en un único sitio (p. ej. si algún día se
 * hace configurable).
 */
const val CORE_BASE_URL = "http://localhost:8080"

/** Cliente HTTP compartido por todas las llamadas al Core. */
val httpClient = OkHttpClient()

/** Conversor JSON compartido. */
val objectMapper = jacksonObjectMapper()

/** Tipo de contenido que enviamos al Core. */
val jsonMediaType = "application/json".toMediaType()
