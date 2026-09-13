package com.apache.notifications

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Toolkit

/**
 * Gestiona las notificaciones nativas de Windows.
 *
 * Utiliza el sistema de notificaciones disponible mediante AWT,
 * evitando añadir dependencias externas al proyecto.
 */
object WindowsNotificationManager {

    private var trayIcon: TrayIcon? = null

    /**
     * Inicializa el icono necesario para mostrar notificaciones.
     */
    fun initialize() {
        if (!SystemTray.isSupported()) {
            return
        }

        if (trayIcon != null) {
            return
        }

        val image = Toolkit.getDefaultToolkit().createImage(ByteArray(0))

        trayIcon =
            TrayIcon(image, "Apache").apply {
                isImageAutoSize = true
            }

        try {
            SystemTray.getSystemTray().add(trayIcon)
        } catch (_: Exception) {
            trayIcon = null
        }
    }

    /**
     * Muestra una notificación nativa del sistema.
     */
    fun show(title: String, message: String) {
        initialize()

        try {
            trayIcon?.displayMessage(
                title,
                message,
                TrayIcon.MessageType.INFO
            )
        } catch (_: Exception) {
            // Si Windows no permite mostrar la notificación,
            // no interrumpimos el funcionamiento de Apache.
        }
    }

    /**
     * Libera los recursos utilizados por el icono de la bandeja.
     */
    fun dispose() {
        trayIcon?.let {
            try {
                SystemTray.getSystemTray().remove(it)
            } catch (_: Exception) {
                // No hacemos nada si Windows ya ha eliminado el icono.
            }
        }

        trayIcon = null
    }
}