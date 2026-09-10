package com.apache

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.apache.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Apache",
        state = WindowState(
            width = 1000.dp,
            height = 700.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        resizable = true
    ) {
        App()
    }
}