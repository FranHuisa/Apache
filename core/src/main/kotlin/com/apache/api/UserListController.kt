package com.apache.api

import com.apache.ApacheDefaults
import com.apache.database.service.ListItemRecord
import com.apache.database.service.ListSummaryRecord
import com.apache.database.service.UserListService
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
 * API de la sección Listas del Desktop.
 *
 *  GET    /api/lists                     -> listas con sus pendientes
 *  GET    /api/lists/items?list=compra   -> elementos de una lista
 *  POST   /api/lists/items               -> añadir {list, items: [...]}
 *  PUT    /api/lists/items/{id}          -> marcar/desmarcar {done}
 *  DELETE /api/lists/items/{id}          -> quitar un elemento
 *  DELETE /api/lists/done?list=compra    -> quitar lo tachado
 */
@RestController
@RequestMapping("/api/lists")
class UserListController(private val lists: UserListService) {

    private val user = ApacheDefaults.DEFAULT_USER_ID

    @GetMapping
    fun summaries(): List<ListSummaryRecord> = lists.lists(user)

    @GetMapping("/items")
    fun items(@RequestParam(defaultValue = "") list: String): List<ListItemRecord> = lists.items(user, list)

    @PostMapping("/items")
    fun add(@RequestBody request: AddListItemsRequest): List<ListItemRecord> =
        lists.add(user, request.list, request.items.orEmpty())

    @PutMapping("/items/{id}")
    fun setDone(@PathVariable id: Long, @RequestBody request: ListItemDoneRequest) {
        if (!lists.setDone(user, id, request.done)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el elemento $id.")
    }

    @DeleteMapping("/items/{id}")
    fun delete(@PathVariable id: Long) {
        if (!lists.delete(user, id)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el elemento $id.")
    }

    @DeleteMapping("/done")
    fun clearDone(@RequestParam(defaultValue = "") list: String): Int = lists.clear(user, list, onlyDone = true)
}

data class AddListItemsRequest(val list: String? = null, val items: List<String>? = null)

data class ListItemDoneRequest(val done: Boolean = true)
