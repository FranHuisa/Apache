package com.apache.mobile.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apache.mobile.ApacheApp
import java.time.format.DateTimeFormatter

/** Llega a la hora de un recordatorio o del comienzo de un evento y muestra el aviso. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ApacheApp
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id <= 0) return

        when (intent.getStringExtra(EXTRA_KIND)) {
            AlarmScheduler.KIND_REMINDER -> {
                val reminder = app.reminders.find(id) ?: return
                if (reminder.status != "pending") return
                app.reminders.setStatus(id, "triggered")
                Notifications.show(context, id.toInt(), Notifications.CHANNEL_REMINDERS, "Recordatorio", reminder.title)
            }

            AlarmScheduler.KIND_EVENT -> {
                val event = app.events.find(id) ?: return
                if (event.status != "confirmed") return
                val hours = DateTimeFormatter.ofPattern("HH:mm")
                val range = event.startAt.format(hours) + (event.endAt?.let { " – ${it.format(hours)}" } ?: "")
                Notifications.show(
                    context, 1_000_000 + id.toInt(), Notifications.CHANNEL_SCHEDULE,
                    "Empieza: ${event.title}",
                    listOfNotNull(range, event.location).joinToString(" · ")
                )
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
    }
}
