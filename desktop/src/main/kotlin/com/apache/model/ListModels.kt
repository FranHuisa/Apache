package com.apache.model

/** Elemento de una lista (GET /api/lists/items). */
data class ListItemDto(val id: Long, val list: String, val title: String, val done: Boolean)

/** Resumen de una lista (GET /api/lists). */
data class ListSummaryDto(val name: String, val pending: Int, val total: Int)
