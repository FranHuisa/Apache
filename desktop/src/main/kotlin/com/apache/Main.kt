package com.apache

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.apache.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Apache"
    ) {
        App()
    }
}