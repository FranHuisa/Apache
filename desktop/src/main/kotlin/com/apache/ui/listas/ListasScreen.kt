package com.apache.ui.listas

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.ListItemDto
import com.apache.ui.theme.ApacheColors
import com.apache.ui.theme.calendarTextFieldColors

/**
 * Sección Listas: compra, tareas y las que crees (o Apache por chat: «apunta
 * leche en la compra»). Clic en un elemento para tacharlo; ✕ para quitarlo.
 */
@Composable
fun ListasScreen(controller: ListsController) {

    LaunchedEffect(Unit) { controller.load() }

    Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Listas", color = Color.White, fontSize = 26.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "También por chat: «apunta pan y leche en la compra», «¿qué me queda pendiente?».",
                    color = ApacheColors.textCalendarMuted, fontSize = 14.sp
                )
            }
            TextButton(onClick = { controller.load() }) { Text("Actualizar", color = ApacheColors.accentLight) }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Listas.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(controller.lists, key = { it.name }) { list ->
                val selected = list.name == controller.current
                val bg by animateColorAsState(if (selected) ApacheColors.accent else ApacheColors.surfaceCard)
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
                        .clickable { controller.select(list.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(list.name.replaceFirstChar { it.titlecase() }, color = if (selected) Color.Black else Color.White, fontSize = 14.sp)
                    if (list.pending > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${list.pending}", color = if (selected) Color.Black else ApacheColors.accentLight, fontSize = 12.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = controller.newList,
                        onValueChange = { controller.newList = it },
                        placeholder = { Text("Nueva lista", fontSize = 13.sp) },
                        singleLine = true,
                        colors = calendarTextFieldColors(),
                        modifier = Modifier.width(170.dp).onEnter { controller.createList() }
                    )
                    TextButton(onClick = { controller.createList() }) { Text("Crear", color = ApacheColors.accentLight) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Añadir a la lista actual.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = controller.newItem,
                onValueChange = { controller.newItem = it },
                placeholder = { Text("Añadir a ${controller.current}…") },
                singleLine = true,
                colors = calendarTextFieldColors(),
                modifier = Modifier.weight(1f).onEnter { controller.addItem() }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = { controller.addItem() },
                colors = ButtonDefaults.buttonColors(containerColor = ApacheColors.accent, contentColor = Color.Black)
            ) { Text("Añadir") }
        }

        controller.error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = ApacheColors.dangerSoft, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        val pending = controller.items.filter { !it.done }
        val done = controller.items.filter { it.done }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            if (controller.items.isEmpty()) {
                item {
                    Surface(color = ApacheColors.surfaceCard, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "La lista «${controller.current}» está vacía.",
                            color = ApacheColors.textCalendarMuted, fontSize = 15.sp, modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
            items(pending, key = { "p${it.id}" }) { item -> ListRow(item, controller) }
            if (done.isNotEmpty()) {
                item(key = "doneHeader") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Hecho (${done.size})", color = ApacheColors.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { controller.clearDone() }) { Text("Quitar tachados", color = ApacheColors.accentLight) }
                    }
                }
                items(done, key = { "d${it.id}" }) { item -> ListRow(item, controller) }
            }
        }
    }
}

@Composable
private fun ListRow(item: ListItemDto, controller: ListsController) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (item.done) ApacheColors.surfaceMuted else ApacheColors.surfaceCardAlt)
            .clickable { controller.toggle(item) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape)
                .background(if (item.done) ApacheColors.accent else Color.Transparent)
                .border(1.5.dp, ApacheColors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) Text("✓", color = Color.Black, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.title,
            color = if (item.done) ApacheColors.textFaint else Color.White,
            fontSize = 15.sp,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        Text(
            "✕", color = ApacheColors.textFaint, fontSize = 15.sp,
            modifier = Modifier.clip(CircleShape).clickable { controller.delete(item) }.padding(6.dp)
        )
    }
}

/** Ejecuta [action] al pulsar Intro en un campo de texto. */
private fun Modifier.onEnter(action: () -> Unit): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
        action()
        true
    } else false
}
