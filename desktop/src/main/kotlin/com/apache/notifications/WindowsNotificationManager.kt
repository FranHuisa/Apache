package com.apache.notifications

/**
 * Muestra notificaciones nativas de Windows.
 *
 * El Desktop utiliza PowerShell para solicitar a Windows una notificación Toast,
 * evitando añadir una dependencia externa únicamente para esta funcionalidad.
 */
object WindowsNotificationManager {

    fun show(title: String, message: String) {
        if (title.isBlank() && message.isBlank()) return

        val safeTitle = escapeXml(title)
        val safeMessage = escapeXml(message)

        val script =
            "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null; " +
            "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null; " +
            "\$xml = New-Object Windows.Data.Xml.Dom.XmlDocument; " +
            "\$xml.LoadXml(\"<toast><visual><binding template='ToastGeneric'><text>$safeTitle</text><text>$safeMessage</text></binding></visual></toast>\"); " +
            "\$toast = [Windows.UI.Notifications.ToastNotification]::new(\$xml); " +
            "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Apache').Show(\$toast)"

        try {
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script
            )
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            // Fallo silencioso: la notificación no debe interrumpir Apache.
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}