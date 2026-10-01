package com.apache.util

import androidx.compose.ui.window.WindowState

/**
 * Referencia al estado de la ventana principal, para poder minimizarla un
 * momento al hacer una captura de pantalla (si no, Apache saldría en la
 * captura tapando lo que el usuario quiere enseñar).
 */
object AppWindow {
    var state: WindowState? = null
}
