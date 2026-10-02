package com.apache.tools

import com.apache.ApacheDefaults
import com.apache.database.service.UserListService
import org.springframework.stereotype.Component

/*
 * Listas del usuario (compra, tareas, maleta...). Las mismas herramientas que
 * en Apache Móvil. Son reversibles: se puede volver a añadir o desmarcar.
 */

private const val USER = ApacheDefaults.DEFAULT_USER_ID

/** "leche, huevos y pan" o ["leche", "huevos"] → lista de textos. */
private fun strings(value: Any?): List<String> = when (value) {
    is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
    is String -> value.split(',', ';', '\n', '·').flatMap { it.split(" y ") }.map { it.trim() }.filter { it.isNotEmpty() }
    else -> emptyList()
}

private val ITEMS_SCHEMA = mapOf(
    "type" to "array",
    "description" to "Cosas, una por elemento.",
    "items" to mapOf("type" to "string")
)

@Component
class AddToListTool(private val lists: UserListService) : Tool {
    override val name = "addToList"
    override val description =
        "Añade una o varias cosas a una lista del usuario: la de la compra ('compra'), tareas pendientes " +
            "('tareas') o cualquier otra ('maleta', 'regalos'...). Para 'apunta…', 'añade… a la lista'. " +
            "Si tiene hora concreta, mejor createReminder."
    override val riskLevel = RiskLevel.REVERSIBLE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "list" to mapOf("type" to "string", "description" to "Nombre de la lista: 'compra', 'tareas'... Por defecto 'tareas'."),
            "items" to ITEMS_SCHEMA
        ),
        "required" to listOf("items")
    )

    override fun execute(args: Map<String, Any?>): String {
        val list = UserListService.listName(args["list"] as? String)
        val items = strings(args["items"])
        if (items.isEmpty()) return "¿Qué quieres que apunte?"
        val added = lists.add(USER, list, items)
        val pending = lists.items(USER, list).count { !it.done }
        return if (added.isEmpty()) "Ya estaba todo en la lista '$list' ($pending pendientes)."
        else "Añadido a '$list': ${added.joinToString { it.title }}. Ahora hay $pending pendientes."
    }
}

@Component
class GetListTool(private val lists: UserListService) : Tool {
    override val name = "getList"
    override val description =
        "Muestra una lista del usuario o, sin nombre, todas sus listas con lo pendiente. " +
            "Para '¿qué tengo que comprar?', '¿qué me queda pendiente?'."
    override val riskLevel = RiskLevel.READ_ONLY
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "list" to mapOf("type" to "string", "description" to "Nombre de la lista. Vacío = resumen de todas.")
        )
    )

    override fun execute(args: Map<String, Any?>): String {
        val requested = (args["list"] as? String)?.trim()?.ifBlank { null }
        if (requested == null) {
            val summaries = lists.lists(USER)
            if (summaries.isEmpty()) return "No tiene ninguna lista todavía."
            return summaries.joinToString("\n") { summary ->
                val pending = lists.items(USER, summary.name).filter { !it.done }
                "Lista '${summary.name}' (${summary.pending} pendientes)" +
                    if (pending.isNotEmpty()) ": ${pending.take(15).joinToString { it.title }}" else ""
            }
        }
        val list = UserListService.listName(requested)
        val items = lists.items(USER, list)
        if (items.isEmpty()) return "La lista '$list' está vacía."
        val pending = items.filter { !it.done }
        val done = items.filter { it.done }
        return buildString {
            appendLine("Lista '$list':")
            if (pending.isEmpty()) appendLine("Nada pendiente.") else pending.forEach { appendLine("- ${it.title}") }
            if (done.isNotEmpty()) appendLine("Hecho: ${done.joinToString { it.title }}")
        }.trim()
    }
}

@Component
class UpdateListItemTool(private val lists: UserListService) : Tool {
    override val name = "updateListItem"
    override val description =
        "Tacha, desmarca o quita cosas de una lista: 'ya he comprado la leche', 'quita el pan de la lista'."
    override val riskLevel = RiskLevel.REVERSIBLE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "items" to ITEMS_SCHEMA,
            "action" to mapOf(
                "type" to "string",
                "description" to "'done' = tachar, 'undo' = volver a pendiente, 'delete' = quitar.",
                "enum" to listOf("done", "undo", "delete")
            ),
            "list" to mapOf("type" to "string", "description" to "Nombre de la lista, si se sabe.")
        ),
        "required" to listOf("items", "action")
    )

    override fun execute(args: Map<String, Any?>): String {
        val list = (args["list"] as? String)?.trim()?.ifBlank { null }?.let { UserListService.listName(it) }
        val action = args["action"] as? String ?: "done"
        val changed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        strings(args["items"]).forEach { text ->
            val item = lists.find(USER, text, list)
            if (item == null) {
                missing += text
            } else {
                when (action) {
                    "delete" -> lists.delete(USER, item.id)
                    "undo" -> lists.setDone(USER, item.id, false)
                    else -> lists.setDone(USER, item.id, true)
                }
                changed += "${item.title} (${item.list})"
            }
        }
        val verb = when (action) { "delete" -> "Quitado"; "undo" -> "Vuelto a pendiente"; else -> "Tachado" }
        return listOfNotNull(
            changed.takeIf { it.isNotEmpty() }?.let { "$verb: ${it.joinToString()}." },
            missing.takeIf { it.isNotEmpty() }?.let { "No encuentro en las listas: ${it.joinToString()}." }
        ).joinToString(" ").ifBlank { "No se ha indicado qué cambiar." }
    }
}

@Component
class ClearListTool(private val lists: UserListService) : Tool {
    override val name = "clearList"
    override val description =
        "Limpia una lista: quita lo ya tachado o, si el usuario lo pide expresamente, la vacía entera."
    override val riskLevel = RiskLevel.REVERSIBLE
    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "list" to mapOf("type" to "string", "description" to "Nombre de la lista."),
            "everything" to mapOf("type" to "boolean", "description" to "true = borrar también lo pendiente.")
        ),
        "required" to listOf("list")
    )

    override fun execute(args: Map<String, Any?>): String {
        val list = UserListService.listName(args["list"] as? String)
        val everything = args["everything"] == true || args["everything"]?.toString() == "true"
        val removed = lists.clear(USER, list, onlyDone = !everything)
        return if (removed == 0) "No había nada que quitar de '$list'." else "Quitados $removed elementos de '$list'."
    }
}
