package com.apache.memory

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** Un recuerdo persistente sobre el usuario, tal y como se expone hacia fuera del repositorio. */
data class UserMemory(val id: Int, val content: String, val createdAt: LocalDateTime)

/**
 * Repositorio de la memoria a largo plazo de Apache: hechos y preferencias sobre el usuario que
 * deben persistir más allá de una única conversación (ver [UserMemories] para el porqué).
 */
@Repository
class UserMemoryRepository {

    /** Guarda un nuevo recuerdo. */
    fun remember(content: String): UserMemory = transaction {
        val now = LocalDateTime.now()

        val id = UserMemories.insert {
            it[UserMemories.content] = content
            it[createdAt] = now
        } get UserMemories.id

        UserMemory(id, content, now)
    }

    /** Recupera todos los recuerdos guardados, del más antiguo al más reciente. */
    fun getAll(): List<UserMemory> = transaction {
        UserMemories
            .selectAll()
            .orderBy(UserMemories.id, SortOrder.ASC)
            .map { UserMemory(it[UserMemories.id], it[UserMemories.content], it[UserMemories.createdAt]) }
    }

    /**
     * Olvida (borra) todos los recuerdos cuyo contenido contenga [query] (sin distinguir
     * mayúsculas/minúsculas). Devuelve cuántos se han borrado.
     */
    fun forgetMatching(query: String): Int = transaction {
        val matches = getAll().filter { it.content.contains(query, ignoreCase = true) }

        matches.forEach { match ->
            UserMemories.deleteWhere { UserMemories.id eq match.id }
        }

        matches.size
    }

    /** Olvida un recuerdo concreto por id. Devuelve true si existía y se ha borrado. */
    fun forgetById(id: Int): Boolean = transaction {
        UserMemories.deleteWhere { UserMemories.id eq id } > 0
    }
}
