package com.apache.network

import com.apache.model.ListItemDto
import com.apache.model.ListSummaryDto
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Llamadas a /api/lists del Core. Bloqueantes: llamar desde Dispatchers.IO.
 */

fun fetchLists(): List<ListSummaryDto> = executeList(Request.Builder().url("$CORE_BASE_URL/api/lists").get().build())

fun fetchListItems(list: String): List<ListItemDto> {
    val url = "$CORE_BASE_URL/api/lists/items".toHttpUrl().newBuilder().addQueryParameter("list", list).build()
    return executeList(Request.Builder().url(url).get().build())
}

fun addListItems(list: String, items: List<String>): List<ListItemDto> {
    val body = objectMapper.writeValueAsString(mapOf("list" to list, "items" to items))
    return executeList(
        Request.Builder().url("$CORE_BASE_URL/api/lists/items").post(body.toRequestBody(jsonMediaType)).build()
    )
}

fun setListItemDone(id: Long, done: Boolean) = executeNoBody(
    Request.Builder().url("$CORE_BASE_URL/api/lists/items/$id")
        .put(objectMapper.writeValueAsString(mapOf("done" to done)).toRequestBody(jsonMediaType)).build()
)

fun deleteListItem(id: Long) = executeNoBody(
    Request.Builder().url("$CORE_BASE_URL/api/lists/items/$id").delete().build()
)

fun clearDoneListItems(list: String) {
    val url = "$CORE_BASE_URL/api/lists/done".toHttpUrl().newBuilder().addQueryParameter("list", list).build()
    executeNoBody(Request.Builder().url(url).delete().build())
}

private inline fun <reified T> executeList(request: Request): T {
    httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw RuntimeException("Error ${response.code}: ${bodyText.ifBlank { "Sin respuesta del Core." }}")
        return objectMapper.readValue(bodyText)
    }
}

private fun executeNoBody(request: Request) {
    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw RuntimeException("Error ${response.code} en las listas.")
    }
}
