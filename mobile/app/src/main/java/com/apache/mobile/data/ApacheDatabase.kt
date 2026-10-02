package com.apache.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDateTime

/**
 * Base de datos local de Apache Móvil (SQLite del propio teléfono).
 *
 * Equivale a las tablas de MySQL del Core de escritorio, pero simplificada
 * para un solo usuario:
 *  - conversation / message: historial del chat.
 *  - memory: lo que Apache recuerda del usuario.
 *  - event: calendario y bloques del horario.
 *  - reminder: recordatorios con aviso.
 *  - task: tareas y listas (compra, pendientes...). Desde la versión 2.
 *  - routine: rutinas por voz ("me voy a dormir" → varios pasos). Desde la versión 3.
 *  - diary: diario, una entrada por día. Desde la versión 3.
 *
 * Se usa SQLite "a mano" (sin Room) para no necesitar procesadores de
 * anotaciones en la compilación.
 */
class ApacheDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE message (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content_json TEXT NOT NULL,
                display_text TEXT,
                images_json TEXT,
                created_at TEXT NOT NULL
            )
            """
        )
        db.execSQL("CREATE INDEX idx_message_conversation ON message(conversation_id)")
        db.execSQL(
            """
            CREATE TABLE memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                memory_key TEXT NOT NULL,
                memory_value TEXT NOT NULL,
                importance REAL NOT NULL DEFAULT 0.5,
                updated_at TEXT NOT NULL
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT,
                location TEXT,
                start_at TEXT NOT NULL,
                end_at TEXT,
                status TEXT NOT NULL DEFAULT 'confirmed'
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE reminder (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                trigger_at TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending'
            )
            """
        )
        createTaskTable(db)
        createRoutineAndDiaryTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migraciones aditivas: nunca se borran datos.
        if (oldVersion < 2) createTaskTable(db)
        if (oldVersion < 3) createRoutineAndDiaryTables(db)
    }

    private fun createRoutineAndDiaryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routine (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trigger_phrase TEXT NOT NULL,
                steps TEXT NOT NULL,
                created_at TEXT NOT NULL,
                last_run TEXT
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS diary (
                day TEXT PRIMARY KEY,
                text TEXT NOT NULL,
                mood TEXT,
                updated_at TEXT NOT NULL
            )
            """
        )
    }

    private fun createTaskTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                list_name TEXT NOT NULL,
                title TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                done_at TEXT
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_task_list ON task(list_name)")
    }

    companion object {
        private const val NAME = "apache.db"
        private const val VERSION = 3

        fun now(): String = LocalDateTime.now().withNano(0).toString()
    }
}

// --- Utilidades para leer cursores ---

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

internal fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))

internal inline fun <T> Cursor.mapRows(block: (Cursor) -> T): List<T> = use {
    val result = mutableListOf<T>()
    while (moveToNext()) result.add(block(this))
    result
}

internal fun contentValues(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
    pairs.forEach { (key, value) ->
        when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Long -> put(key, value)
            is Int -> put(key, value)
            is Double -> put(key, value)
            is Boolean -> put(key, if (value) 1 else 0)
            else -> put(key, value.toString())
        }
    }
}
