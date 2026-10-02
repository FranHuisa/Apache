package com.apache.database.service

import java.text.Normalizer
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Service

/** Tabla `user_list_item` (la crea DatabaseFactory al arrancar). */
object UserListItems : Table("user_list_item") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val listName = varchar("list_name", 100)
    val title = varchar("title", 255)
    val done = bool("done")
    val createdAt = datetime("created_at")
    val doneAt = datetime("done_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Un elemento de una lista. */
data class ListItemRecord(val id: Long, val list: String, val title: String, val done: Boolean)

/** Resumen de una lista: pendientes y total. */
data class ListSummaryRecord(val name: String, val pending: Int, val total: Int)

/**
 * Listas del usuario: "compra", "tareas", "maleta"... Misma lógica que
 * TaskStore de Apache Móvil.
 */
@Service
class UserListService {

    /** Añade elementos; los que ya estaban pendientes no se repiten. */
    fun add(userId: Long, list: String?, titles: List<String>): List<ListItemRecord> {
        val name = listName(list)
        val existing = items(userId, name).filter { !it.done }.map { key(it.title) }.toMutableSet()
        val now = LocalDateTime.now().withNano(0)
        return titles.map { it.trim().take(255) }.filter { it.isNotEmpty() && existing.add(key(it)) }.map { title ->
            val id = transaction {
                UserListItems.insert {
                    it[UserListItems.userId] = userId
                    it[listName] = name
                    it[UserListItems.title] = title
                    it[done] = false
                    it[createdAt] = now
                } get UserListItems.id
            }
            ListItemRecord(id, name, title, false)
        }
    }

    fun items(userId: Long, list: String?): List<ListItemRecord> = transaction {
        UserListItems.selectAll()
            .where { (UserListItems.userId eq userId) and (UserListItems.listName eq listName(list)) }
            .orderBy(UserListItems.done to SortOrder.ASC, UserListItems.id to SortOrder.ASC)
            .map { it.toRecord() }
    }

    private fun all(userId: Long): List<ListItemRecord> = transaction {
        UserListItems.selectAll()
            .where { UserListItems.userId eq userId }
            .orderBy(UserListItems.done to SortOrder.ASC, UserListItems.id to SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun lists(userId: Long): List<ListSummaryRecord> =
        all(userId).groupBy { it.list }
            .map { (name, items) -> ListSummaryRecord(name, items.count { !it.done }, items.size) }
            .sortedWith(compareByDescending<ListSummaryRecord> { it.pending }.thenBy { it.name })

    fun setDone(userId: Long, id: Long, done: Boolean): Boolean = transaction {
        UserListItems.update({ (UserListItems.id eq id) and (UserListItems.userId eq userId) }) {
            it[UserListItems.done] = done
            it[doneAt] = if (done) LocalDateTime.now().withNano(0) else null
        } > 0
    }

    fun delete(userId: Long, id: Long): Boolean = transaction {
        UserListItems.deleteWhere { (UserListItems.id eq id) and (UserListItems.userId eq userId) } > 0
    }

    /** Quita lo tachado (o todo si [onlyDone] es false). Devuelve cuántos. */
    fun clear(userId: Long, list: String?, onlyDone: Boolean = true): Int {
        val name = listName(list)
        return transaction {
            if (onlyDone) {
                UserListItems.deleteWhere {
                    (UserListItems.userId eq userId) and (listName eq name) and (done eq true)
                }
            } else {
                UserListItems.deleteWhere { (UserListItems.userId eq userId) and (listName eq name) }
            }
        }
    }

    /** Busca por texto aproximado ("leche" encuentra "Leche desnatada"). */
    fun find(userId: Long, text: String, list: String? = null): ListItemRecord? {
        val wanted = key(text)
        if (wanted.isEmpty()) return null
        val candidates = (if (list != null) items(userId, list) else all(userId)).sortedBy { it.done }
        return candidates.firstOrNull { key(it.title) == wanted }
            ?: candidates.firstOrNull { key(it.title).contains(wanted) }
            ?: candidates.firstOrNull { wanted.contains(key(it.title)) }
    }

    private fun ResultRow.toRecord() = ListItemRecord(
        id = this[UserListItems.id],
        list = this[UserListItems.listName],
        title = this[UserListItems.title],
        done = this[UserListItems.done]
    )

    companion object {
        const val DEFAULT_LIST = "tareas"

        /** "Lista de la compra" → "compra"; vacío → "tareas". */
        fun listName(raw: String?): String {
            var name = raw?.trim()?.lowercase().orEmpty()
            listOf("lista de la ", "lista de los ", "lista de las ", "lista del ", "lista de ", "la lista ", "lista ")
                .forEach { if (name.startsWith(it)) name = name.removePrefix(it) }
            name = name.trim().take(100)
            return when (name) {
                "", "pendientes", "cosas pendientes", "to do", "todo", "tarea" -> DEFAULT_LIST
                "la compra", "compras", "súper", "super", "supermercado" -> "compra"
                else -> name
            }
        }

        private fun key(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()
    }
}
