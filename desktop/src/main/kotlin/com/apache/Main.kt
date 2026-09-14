package com.apache

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import com.apache.ui.App

fun main() = application {

    val windowState = rememberWindowState(
        width = 1400.dp,
        height = 850.dp,
        position = WindowPosition.Aligned(
            androidx.compose.ui.Alignment.Center
        )
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Apache",
        state = windowState
    ) {
        App()
    }
}