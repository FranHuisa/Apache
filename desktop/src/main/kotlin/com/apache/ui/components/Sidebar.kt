package com.apache.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.ui.theme.ApacheColors

/** Secciones disponibles en la barra lateral. */
val sidebarSections = listOf("Chat", "Calendario", "Memoria", "Ayuda")

/** Barra lateral de navegación entre secciones de Apache Desktop. */
@Composable
fun Sidebar(selectedSection: String, onSectionSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(ApacheColors.sidebarBackground)
            .padding(20.dp)
    ) {
        Text(text = "APACHE", color = ApacheColors.accent, fontSize = 24.sp)

        Spacer(modifier = Modifier.height(30.dp))

        sidebarSections.forEach { section ->
            Text(
                text = section,
                color = if (selectedSection == section) Color.White else Color(0xFFAAAAAA),
                fontSize = 16.sp,
                modifier = Modifier.clickable { onSectionSelected(section) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Empuja la versión hacia la parte inferior.
        Spacer(modifier = Modifier.weight(1f))

        Text(text = "Apache 0.1.0", color = ApacheColors.textFaint, fontSize = 12.sp)
    }
}
