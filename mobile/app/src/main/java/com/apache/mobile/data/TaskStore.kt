package com.apache.mobile.data

import java.text.Normalizer

/**
 * Tareas y listas: "compra", "tareas", "maleta"... Cada elemento pertenece a
 * una lista (por nombre) y se puede marcar como hecho.
 */
class TaskStore(private val db: ApacheDatabase) {

    /** Añade elementos a una lista. Los que ya estaban pendientes no se repiten. */
    fun add(list: String, titles: List<String>): List<TaskItem> {
        val name = listName(list)
        val existing = items(name).filter { !it.done }.map { key(it.title) }.toMutableSet()
        return titles.map { it.trim() }.filter { it.isNotEmpty() && existing.add(key(it)) }.map { title ->
            val id = db.writableDatabase.insert(
                "task", null,
                contentValues("list_name" to name, "title" to title, "created_at" to ApacheDatabase.now())
            )
            TaskItem(id, name, title, false)
        }
    }

    /** Elementos de una lista: primero los pendientes, luego los hechos. */
    fun items(list: String): List<TaskItem> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM task WHERE list_name = ? ORDER BY done, id", arrayOf(listName(list))
        ).mapRows { it.toItem() }

    /** Pendientes de una lista con la fecha en que se apuntaron (para los avisos proactivos). */
    fun pendingSince(list: String): List<Pair<TaskItem, java.time.LocalDateTime>> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM task WHERE list_name = ? AND done = 0 ORDER BY created_at", arrayOf(listName(list))
        ).mapRows { it.toItem() to java.time.LocalDateTime.parse(it.string("created_at")) }

    fun pending(): List<TaskItem> =
        db.readableDatabase.rawQuery("SELECT * FROM task WHERE done = 0 ORDER BY list_name, id", null)
            .mapRows { it.toItem() }

    /** Todas las listas con su número de pendientes (las que tienen algo). */
    fun lists(): List<TaskListSummary> =
        db.readableDatabase.rawQuery(
            "SELECT list_name, SUM(CASE WHEN done = 0 THEN 1 ELSE 0 END) AS pending, COUNT(*) AS total " +
                "FROM task GROUP BY list_name ORDER BY pending DESC, list_name",
            null
        ).mapRows { TaskListSummary(it.string("list_name"), it.long("pending").toInt(), it.long("total").toInt()) }

    fun setDone(id: Long, done: Boolean): Boolean =
        db.writableDatabase.update(
            "task",
            contentValues("done" to done, "done_at" to if (done) ApacheDatabase.now() else null),
            "id = ?", arrayOf(id.toString())
        ) > 0

    fun delete(id: Long): Boolean =
        db.writableDatabase.delete("task", "id = ?", arrayOf(id.toString())) > 0

    /** Borra los hechos de una lista (o la lista entera si [onlyDone] es false). Devuelve cuántos. */
    fun clear(list: String, onlyDone: Boolean = true): Int =
        if (onlyDone) db.writableDatabase.delete("task", "list_name = ? AND done = 1", arrayOf(listName(list)))
        else db.writableDatabase.delete("task", "list_name = ?", arrayOf(listName(list)))

    /** Busca un elemento por texto aproximado ("leche" encuentra "Leche desnatada"). */
    fun find(text: String, list: String? = null): TaskItem? {
        val wanted = key(text)
        if (wanted.isEmpty()) return null
        val candidates = if (list != null) items(list) else all()
        return candidates.sortedBy { it.done }.let { sorted ->
            sorted.firstOrNull { key(it.title) == wanted } ?: sorted.firstOrNull { key(it.title).contains(wanted) }
                ?: sorted.firstOrNull { wanted.contains(key(it.title)) }
        }
    }

    private fun all(): List<TaskItem> =
        db.readableDatabase.rawQuery("SELECT * FROM task ORDER BY done, id", null).mapRows { it.toItem() }

    private fun android.database.Cursor.toItem() = TaskItem(
        id = long("id"),
        list = string("list_name"),
        title = string("title"),
        done = long("done") == 1L
    )

    companion object {
        const val DEFAULT_LIST = "tareas"

        /** "Lista de la compra" → "compra"; vacío → "tareas". */
        fun listName(raw: String?): String {
            var name = raw?.trim()?.lowercase().orEmpty()
            listOf("lista de la ", "lista de los ", "lista de las ", "lista del ", "lista de ", "la lista ", "lista ")
                .forEach { if (name.startsWith(it)) name = name.removePrefix(it) }
            name = name.trim()
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
