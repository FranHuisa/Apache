package com.apache.ui.listas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apache.model.ListItemDto
import com.apache.model.ListSummaryDto
import com.apache.network.addListItems
import com.apache.network.clearDoneListItems
import com.apache.network.deleteListItem
import com.apache.network.fetchListItems
import com.apache.network.fetchLists
import com.apache.network.setListItemDone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de la sección Listas (compra, tareas y las que cree el usuario). */
class ListsController(private val scope: CoroutineScope) {

    var lists by mutableStateOf<List<ListSummaryDto>>(emptyList())
        private set
    var current by mutableStateOf("tareas")
        private set
    var items by mutableStateOf<List<ListItemDto>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)

    var newItem by mutableStateOf("")
    var newList by mutableStateOf("")

    fun load() {
        scope.launch {
            try {
                val (summaries, loaded) = withContext(Dispatchers.IO) { fetchLists() to fetchListItems(current) }
                // "compra" y "tareas" siempre aparecen, aunque estén vacías.
                val names = (listOf("compra", "tareas") + summaries.map { it.name }).distinct()
                lists = names.map { name -> summaries.firstOrNull { it.name == name } ?: ListSummaryDto(name, 0, 0) }
                items = loaded
                error = null
            } catch (_: Exception) {
                error = "No se han podido cargar las listas. Comprueba que el Core está arrancado."
            }
        }
    }

    fun select(list: String) {
        current = list
        load()
    }

    fun createList() {
        val name = newList.trim().lowercase()
        if (name.isEmpty()) return
        newList = ""
        current = name
        items = emptyList()
        if (lists.none { it.name == name }) lists = lists + ListSummaryDto(name, 0, 0)
    }

    fun addItem() {
        val text = newItem.trim()
        if (text.isEmpty()) return
        newItem = ""
        change { addListItems(current, listOf(text)) }
    }

    fun toggle(item: ListItemDto) = change { setListItemDone(item.id, !item.done) }

    fun delete(item: ListItemDto) = change { deleteListItem(item.id) }

    fun clearDone() = change { clearDoneListItems(current) }

    private fun change(block: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (_: Exception) {
                error = "No se ha podido guardar el cambio."
            }
            load()
        }
    }
}
