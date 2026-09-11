package com.apache.api

import com.apache.memory.UserMemoryRepository
import org.springframework.web.bind.annotation.*

/**
 * Expone la memoria a largo plazo de Apache (ver [UserMemoryRepository]) a la UI de escritorio,
 * para la sección "Memoria": listar qué recuerda Apache del usuario y poder borrar algo a mano
 * sin tener que pedírselo por chat.
 *
 * Guardar un recuerdo nuevo NO se expone aquí a propósito: solo Apache decide qué merece la
 * pena recordar, a través de la tool rememberFact durante una conversación.
 */
@RestController
@RequestMapping("/api/memory")
class MemoryController(private val userMemoryRepository: UserMemoryRepository) {

    @GetMapping
    fun list(): List<MemoryItemResponse> =
        userMemoryRepository.getAll().map { MemoryItemResponse(id = it.id, content = it.content) }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Int): MemoryDeleteResponse =
        MemoryDeleteResponse(deleted = userMemoryRepository.forgetById(id))
}

data class MemoryItemResponse(val id: Int, val content: String)

data class MemoryDeleteResponse(val deleted: Boolean)
