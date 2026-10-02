package com.apache.mobile.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apache.mobile.ApacheApp
import java.time.LocalDateTime

/** Al reiniciar el móvil, Android borra las alarmas: se vuelven a programar. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as ApacheApp
        app.reminders.pending().forEach { app.alarms.scheduleReminder(it) }

        val now = LocalDateTime.now()
        app.events.between(now, now.plusDays(60)).forEach { app.alarms.scheduleEvent(it) }
        app.alarms.scheduleBriefing()
    }
}
