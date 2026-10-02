package com.apache.mobile.tools

import com.apache.mobile.ApacheApp
import com.apache.mobile.ai.GeminiClient
import org.json.JSONArray

/** Catálogo de herramientas de Apache Móvil. */
class ToolRegistry(private val tools: List<Tool>) {

    private val byName = tools.associateBy { it.name }

    fun find(name: String): Tool? = byName[name]

    fun declarations(): JSONArray = JSONArray().apply { tools.forEach { put(it.declaration()) } }

    companion object {
        fun create(app: ApacheApp, gemini: GeminiClient) = ToolRegistry(
            listOf(
                GetWeatherTool(),
                GetNewsTool(),
                GetMyLocationTool(),
                ReadWebPageTool(),
                GetBriefingTool(app),
                SetBriefingTool(app),
                AddToListTool(app),
                GetListTool(app),
                UpdateListItemTool(app),
                ClearListTool(app),
                CreateRoutineTool(app),
                ListRoutinesTool(app),
                DeleteRoutineTool(app),
                SaveDiaryEntryTool(app),
                SearchDiaryTool(app),
                GetAlertsTool(app),
                WebSearchTool(gemini),
                SearchImagesTool(gemini),
                SaveImageTool(app),
                RememberFactTool(app),
                ForgetFactTool(app),
                GetCalendarEventsTool(app),
                CreateCalendarEventTool(app),
                UpdateCalendarEventTool(app),
                DeleteCalendarEventTool(app),
                PlanDayScheduleTool(app),
                CreateReminderTool(app),
                ListRemindersTool(app),
                CancelReminderTool(app),
                OpenAppTool(app),
                SetAlarmTool(app),
                SetTimerTool(app),
                PlaySongTool(app)
            )
        )
    }
}
