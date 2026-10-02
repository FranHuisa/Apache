package com.apache.mobile.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.apache.mobile.MainActivity

/** Canales y envío de notificaciones (recordatorios y bloques del horario). */
object Notifications {

    const val CHANNEL_REMINDERS = "recordatorios"
    const val CHANNEL_SCHEDULE = "horario"
    const val CHANNEL_BRIEFING = "resumen"
    private const val BRIEFING_NOTIFICATION = 3_000_000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCHEDULE, "Horario", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BRIEFING, "Resumen de buenos días", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun show(context: Context, id: Int, channel: String, title: String, text: String) {
        // Android 13+: sin permiso de notificaciones no se puede avisar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** El resumen de buenos días (sustituye al del día anterior si sigue ahí). */
    fun showBriefing(context: Context, briefing: com.apache.mobile.reminders.BriefingResult) =
        show(context, BRIEFING_NOTIFICATION, CHANNEL_BRIEFING, briefing.title, briefing.text)
}
