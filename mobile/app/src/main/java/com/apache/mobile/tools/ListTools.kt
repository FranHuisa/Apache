package com.apache.mobile.tools

import com.apache.mobile.ApacheApp
import com.apache.mobile.data.TaskStore
import com.apache.mobile.reminders.Briefing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Tareas y listas
// ---------------------------------------------------------------------------

private fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key)
    if (array != null) return (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }
    // A veces llega como texto: "leche, huevos y pan".
    return optString(key, "").split(',', ';', '\n', '·').flatMap { it.split(" y ") }.map { it.trim() }.filter { it.isNotEmpty() }
}

class AddToListTool(private val app: ApacheApp) : Tool {
    override val name = "addToList"
    override val description =
        "Añade una o varias cosas a una lista del usuario: la de la compra ('compra'), tareas pendientes " +
            "('tareas') o cualquier otra ('maleta', 'regalos'...). Para 'apunta…', 'añade… a la lista', " +
            "'tengo que…' sin hora concreta (si hay hora, mejor createReminder)."
    override val parameters = Schema.obj(
        "list" to Schema.string("Nombre de la lista: 'compra', 'tareas', 'maleta'... Por defecto 'tareas'."),
        "items" to Schema.array("Cosas a añadir, una por elemento.", Schema.string("Elemento")),
        required = listOf("items")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val list = TaskStore.listName(args.str("list"))
        val items = args.strings("items")
        if (items.isEmpty()) return@withContext "¿Qué quieres que apunte?"
        val added = app.tasks.add(list, items)
        val pending = app.tasks.items(list).count { !it.done }
        when {
            added.isEmpty() -> "Ya estaba todo en la lista '$list' ($pending pendientes)."
            else -> "Añadido a '$list': ${added.joinToString { it.title }}. Ahora hay $pending pendientes."
        }
    }
}

class GetListTool(private val app: ApacheApp) : Tool {
    override val name = "getList"
    override val description =
        "Muestra una lista del usuario (pendientes y hechos) o, sin nombre, todas sus listas con lo que " +
            "queda pendiente. Para '¿qué tengo que comprar?', '¿qué me queda pendiente?'."
    override val parameters = Schema.obj(
        "list" to Schema.string("Nombre de la lista. Vacío = resumen de todas.")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val name = args.str("list")
        if (name == null) {
            val lists = app.tasks.lists()
            if (lists.isEmpty()) return@withContext "No tiene ninguna lista todavía."
            return@withContext buildString {
                lists.forEach { summary ->
                    val pending = app.tasks.items(summary.name).filter { !it.done }
                    append("Lista '${summary.name}' (${summary.pending} pendientes)")
                    if (pending.isNotEmpty()) append(": ${pending.take(15).joinToString { it.title }}")
                    appendLine()
                }
            }.trim()
        }
        val list = TaskStore.listName(name)
        val items = app.tasks.items(list)
        if (items.isEmpty()) return@withContext "La lista '$list' está vacía."
        val pending = items.filter { !it.done }
        val done = items.filter { it.done }
        buildString {
            appendLine("Lista '$list':")
            if (pending.isEmpty()) appendLine("Nada pendiente.") else pending.forEach { appendLine("- ${it.title}") }
            if (done.isNotEmpty()) appendLine("Hecho: ${done.joinToString { it.title }}")
        }.trim()
    }
}

class UpdateListItemTool(private val app: ApacheApp) : Tool {
    override val name = "updateListItem"
    override val description =
        "Tacha, desmarca o quita cosas de una lista: 'ya he comprado la leche', 'quita el pan de la lista', " +
            "'he terminado el informe'."
    override val parameters = Schema.obj(
        "items" to Schema.array("Cosas a cambiar (el texto aproximado vale).", Schema.string("Elemento")),
        "action" to Schema.string("'done' = tachar, 'undo' = volver a pendiente, 'delete' = quitar.", listOf("done", "undo", "delete")),
        "list" to Schema.string("Nombre de la lista, si se sabe."),
        required = listOf("items", "action")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val list = args.str("list")?.let { TaskStore.listName(it) }
        val action = args.str("action") ?: "done"
        val changed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        args.strings("items").forEach { text ->
            val item = app.tasks.find(text, list)
            if (item == null) {
                missing += text
            } else {
                when (action) {
                    "delete" -> app.tasks.delete(item.id)
                    "undo" -> app.tasks.setDone(item.id, false)
                    else -> app.tasks.setDone(item.id, true)
                }
                changed += "${item.title} (${item.list})"
            }
        }
        val verb = when (action) { "delete" -> "Quitado"; "undo" -> "Vuelto a pendiente"; else -> "Tachado" }
        listOfNotNull(
            changed.takeIf { it.isNotEmpty() }?.let { "$verb: ${it.joinToString()}." },
            missing.takeIf { it.isNotEmpty() }?.let { "No encuentro en las listas: ${it.joinToString()}." }
        ).joinToString(" ").ifBlank { "No se ha indicado qué cambiar." }
    }
}

class ClearListTool(private val app: ApacheApp) : Tool {
    override val name = "clearList"
    override val description =
        "Limpia una lista: quita lo ya tachado o, si el usuario lo pide expresamente, la vacía entera."
    override val parameters = Schema.obj(
        "list" to Schema.string("Nombre de la lista."),
        "everything" to Schema.boolean("true = borrar también lo pendiente. Por defecto solo lo tachado."),
        required = listOf("list")
    )

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val list = TaskStore.listName(args.str("list"))
        val everything = args.optBoolean("everything", false)
        val removed = app.tasks.clear(list, onlyDone = !everything)
        if (removed == 0) "No había nada que quitar de '$list'." else "Quitados $removed elementos de '$list'."
    }
}

// ---------------------------------------------------------------------------
// Resumen de buenos días
// ---------------------------------------------------------------------------

class GetBriefingTool(private val app: ApacheApp) : Tool {
    override val name = "getBriefing"
    override val description =
        "Resumen del día: agenda de hoy, tiempo de donde está, recordatorios, listas pendientes y 3 titulares. " +
            "Para 'dame mi resumen', '¿cómo pinta el día?', 'buenos días'."
    override val parameters = Schema.obj()

    override suspend fun execute(args: JSONObject): String = Briefing.build(app).text
}

class SetBriefingTool(private val app: ApacheApp) : Tool {
    override val name = "setBriefing"
    override val description =
        "Cambia el resumen de buenos días (una notificación diaria): la hora o activarlo/desactivarlo."
    override val parameters = Schema.obj(
        "time" to Schema.string("Hora 'HH:mm', ej: '07:30'."),
        "enabled" to Schema.boolean("true = activado, false = desactivado.")
    )

    override suspend fun execute(args: JSONObject): String {
        args.str("time")?.let { raw ->
            val time = parseTime(raw) ?: return "La hora '$raw' no es válida (usa HH:mm)."
            app.settings.briefingTime = "%02d:%02d".format(time.hour, time.minute)
        }
        if (args.has("enabled")) app.settings.briefingEnabled = args.optBoolean("enabled", true)
        app.alarms.scheduleBriefing()
        return if (app.settings.briefingEnabled) "Resumen de buenos días activado a las ${app.settings.briefingTime}."
        else "Resumen de buenos días desactivado."
    }
}
