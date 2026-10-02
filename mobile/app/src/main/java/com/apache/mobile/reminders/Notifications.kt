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

/** Canales y envío de notificaciones (recordatorios, horario, resumen, diario y avisos). */
object Notifications {

    const val CHANNEL_REMINDERS = "recordatorios"
    const val CHANNEL_SCHEDULE = "horario"
    const val CHANNEL_BRIEFING = "resumen"
    const val CHANNEL_DIARY = "diario"
    const val CHANNEL_PROACTIVE = "avisos"

    /** Al abrir la app desde el aviso del diario, el chat pregunta "¿qué tal el día?". */
    const val EXTRA_OPEN = "apache_open"
    const val OPEN_DIARY = "diary"

    private const val BRIEFING_NOTIFICATION = 3_000_000
    private const val DIARY_NOTIFICATION = 3_100_000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_SCHEDULE, "Horario", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_BRIEFING, "Resumen de buenos días", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_DIARY, "Diario", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_PROACTIVE, "Avisos de Apache", NotificationManager.IMPORTANCE_DEFAULT)
        ).forEach { manager.createNotificationChannel(it) }
    }

    /** Botón extra de una notificación ("Poner alarma a las 7:00"). */
    class Action(val label: String, val intent: PendingIntent)

    fun show(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        open: String? = null,
        action: Action? = null
    ) {
        // Android 13+: sin permiso de notificaciones no se puede avisar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (open != null) openIntent.putExtra(EXTRA_OPEN, open)
        val contentIntent = PendingIntent.getActivity(
            context, id, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        action?.let { builder.addAction(0, it.label, it.intent) }

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** El resumen de buenos días (sustituye al del día anterior si sigue ahí). */
    fun showBriefing(context: Context, briefing: BriefingResult) =
        show(context, BRIEFING_NOTIFICATION, CHANNEL_BRIEFING, briefing.title, briefing.text)

    /** "¿Qué tal el día?": al tocarlo, el chat lo pregunta y se guarda en el diario. */
    fun showDiaryPrompt(context: Context) =
        show(
            context, DIARY_NOTIFICATION, CHANNEL_DIARY,
            "¿Qué tal el día? 📓",
            "Cuéntamelo en un par de frases y lo apunto en tu diario.",
            open = OPEN_DIARY
        )
}
