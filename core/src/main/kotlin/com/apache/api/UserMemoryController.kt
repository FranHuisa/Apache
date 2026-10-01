package com.apache.api

import com.apache.ApacheDefaults
import com.apache.database.repository.MemoryRecord
import com.apache.database.service.UserMemoryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * API de la sección Memoria del Desktop.
 *
 *  GET    /api/memory                          -> datos que Apache recuerda del usuario
 *  POST   /api/memory                          -> añadir (o actualizar por clave) un dato
 *  PUT    /api/memory/{id}                     -> editar un dato
 *  DELETE /api/memory/{id}                     -> olvidar un dato
 *  GET    /api/memory/conversations            -> conversaciones pasadas
 *  GET    /api/memory/conversations/{id}       -> mensajes visibles de una conversación
 */
@RestController
@RequestMapping("/api/memory")
class UserMemoryController(
    private val memoryService: UserMemoryService
) {

    @GetMapping
    fun list(): List<MemoryResponse> =
        memoryService.list(ApacheDefaults.DEFAULT_USER_ID).map { it.toResponse() }

    @PostMapping
    fun create(@RequestBody request: MemoryRequest): MemoryResponse =
        try {
            memoryService.remember(
                userId = ApacheDefaults.DEFAULT_USER_ID,
                key = request.key.orEmpty(),
                value = request.value.orEmpty(),
                type = request.type ?: UserMemoryService.DEFAULT_TYPE,
                importance = request.importance ?: UserMemoryService.DEFAULT_IMPORTANCE
            ).first.toResponse()
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: MemoryRequest): MemoryResponse =
        try {
            memoryService.update(id, request.key, request.value, request.type, request.importance)
                ?.toResponse()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el dato $id.")
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): Map<String, Boolean> =
        mapOf("deleted" to memoryService.forget(id))

    @GetMapping("/conversations")
    fun conversations(@RequestParam(defaultValue = "50") limit: Int): List<ConversationSummaryResponse> =
        memoryService.conversations(ApacheDefaults.DEFAULT_USER_ID, limit.coerceIn(1, 200)).map {
            ConversationSummaryResponse(
                id = it.id,
                title = it.title,
                messageCount = it.messageCount,
                createdAt = it.createdAt.toString(),
                updatedAt = it.updatedAt.toString()
            )
        }

    @GetMapping("/conversations/{id}")
    fun conversationMessages(@PathVariable id: Long): List<ConversationMessageResponse> =
        memoryService.conversationMessages(id).map {
            ConversationMessageResponse(role = it.role, text = it.text, createdAt = it.createdAt.toString())
        }
}

data class MemoryRequest(
    val key: String? = null,
    val value: String? = null,
    val type: String? = null,
    val importance: Double? = null
)

data class MemoryResponse(
    val id: Long,
    val key: String,
    val value: String,
    val type: String,
    val importance: Double,
    val updatedAt: String
)

data class ConversationSummaryResponse(
    val id: Long,
    val title: String,
    val messageCount: Int,
    val createdAt: String,
    val updatedAt: String
)

data class ConversationMessageResponse(
    val role: String,
    val text: String,
    val createdAt: String
)

private fun MemoryRecord.toResponse() = MemoryResponse(
    id = id,
    key = key,
    value = value,
    type = type,
    importance = importance,
    updatedAt = updatedAt.toString()
)
