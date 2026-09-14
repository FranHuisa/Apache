package com.apache

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import com.apache.core.CoreProcessManager
import com.apache.ui.App

fun main() = application {

    // Inicia el Core automáticamente antes de mostrar la interfaz.
    CoreProcessManager.start()

    val windowState = rememberWindowState(
        width = 1400.dp,
        height = 850.dp,
        position = WindowPosition.Aligned(
            androidx.compose.ui.Alignment.Center
        )
    )

    Window(
        onCloseRequest = {
            // Cierra el Core que haya sido iniciado por Apache Desktop.
            CoreProcessManager.stop()

            exitApplication()
        },
        title = "Apache",
        state = windowState
    ) {
        App()
    }
}