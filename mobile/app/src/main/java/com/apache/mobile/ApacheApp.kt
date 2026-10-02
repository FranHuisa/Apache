package com.apache.mobile

import android.app.Application
import com.apache.mobile.ai.Agent
import com.apache.mobile.ai.GeminiClient
import com.apache.mobile.data.ApacheDatabase
import com.apache.mobile.data.ConversationStore
import com.apache.mobile.data.EventStore
import com.apache.mobile.data.MemoryStore
import com.apache.mobile.data.ReminderStore
import com.apache.mobile.data.Settings
import com.apache.mobile.data.DiaryStore
import com.apache.mobile.data.RoutineStore
import com.apache.mobile.data.TaskStore
import com.apache.mobile.reminders.AlarmScheduler
import com.apache.mobile.reminders.Notifications
import com.apache.mobile.tools.ToolRegistry

/**
 * Punto de entrada de la app: crea una sola vez la base de datos, los ajustes,
 * el cliente de Gemini y el agente, y los deja accesibles con [ApacheApp.get].
 *
 * Es el equivalente móvil del "Core" de escritorio, pero dentro del teléfono.
 */
class ApacheApp : Application() {

    lateinit var settings: Settings
        private set
    lateinit var database: ApacheDatabase
        private set
    lateinit var conversations: ConversationStore
        private set
    lateinit var memory: MemoryStore
        private set
    lateinit var events: EventStore
        private set
    lateinit var reminders: ReminderStore
        private set
    lateinit var tasks: TaskStore
        private set
    lateinit var routines: RoutineStore
        private set
    lateinit var diary: DiaryStore
        private set
    lateinit var alarms: AlarmScheduler
        private set
    lateinit var agent: Agent
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        settings = Settings(this)
        database = ApacheDatabase(this)
        conversations = ConversationStore(database)
        memory = MemoryStore(database)
        events = EventStore(database)
        reminders = ReminderStore(database)
        tasks = TaskStore(database)
        routines = RoutineStore(database)
        diary = DiaryStore(database)
        alarms = AlarmScheduler(this)

        Notifications.createChannels(this)
        alarms.scheduleBriefing()
        alarms.scheduleDiary()
        alarms.scheduleProactive()

        val gemini = GeminiClient(settings)
        val tools = ToolRegistry.create(this, gemini)
        agent = Agent(gemini, tools, conversations, memory, settings)
    }

    companion object {
        private lateinit var instance: ApacheApp

        fun get(): ApacheApp = instance
    }
}
