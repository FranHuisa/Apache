package com.apache.mobile.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.apache.mobile.data.CalendarEvent
import com.apache.mobile.data.Reminder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programa los avisos con AlarmManager: recordatorios y comienzo de eventos o
 * bloques del horario. Cuando llega la hora, [ReminderReceiver] muestra la
 * notificación (aunque la app esté cerrada).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleReminder(reminder: Reminder) =
        schedule(KIND_REMINDER, reminder.id, reminder.triggerAt)

    fun cancelReminder(id: Long) = cancel(KIND_REMINDER, id)

    fun scheduleEvent(event: CalendarEvent) {
        if (event.status != "confirmed") cancelEvent(event.id) else schedule(KIND_EVENT, event.id, event.startAt)
    }

    fun cancelEvent(id: Long) = cancel(KIND_EVENT, id)

    private fun schedule(kind: String, id: Long, at: LocalDateTime) {
        if (at.isBefore(LocalDateTime.now())) return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pendingIntent(kind, id)

        // Hora exacta si el sistema lo permite; si no, aproximada (Android puede retrasarla unos minutos).
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    private fun cancel(kind: String, id: Long) = alarmManager.cancel(pendingIntent(kind, id))

    private fun pendingIntent(kind: String, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_KIND, kind)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        // requestCode distinto para recordatorios y eventos con el mismo id.
        val requestCode = (if (kind == KIND_EVENT) 1_000_000 else 0) + (id % 1_000_000).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val KIND_REMINDER = "reminder"
        const val KIND_EVENT = "event"
    }
}
