package com.apache.ui.memoria

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.ui.theme.ApacheColors

@Composable
fun MemoriaScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Memoria", color = Color.White, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "La memoria de Apache estará disponible próximamente.",
            color = ApacheColors.textMuted,
            fontSize = 15.sp
        )
    }
}
