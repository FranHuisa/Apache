package com.apache.mobile.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import java.net.URLEncoder
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Lanza una actividad desde la app (FLAG_ACTIVITY_NEW_TASK porque se usa el contexto de la aplicación). */
private fun Context.launch(intent: Intent): Boolean =
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

/** Abre una app instalada por su nombre ("abre WhatsApp"). */
class OpenAppTool(private val context: Context) : Tool {
    override val name = "openApp"
    override val description = "Abre una aplicación del móvil por su nombre, ej: 'WhatsApp', 'cámara', 'ajustes'."
    override val direct = true
    override val parameters = Schema.obj("app" to Schema.string("Nombre de la app."), required = listOf("app"))

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val wanted = normalize(args.str("app") ?: return@withContext "¿Qué app abro?")
        val pm = context.packageManager

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0).map { info ->
            normalize(info.loadLabel(pm).toString()) to info.activityInfo.packageName
        }

        val match = apps.firstOrNull { it.first == wanted }
            ?: apps.firstOrNull { it.first.startsWith(wanted) }
            ?: apps.firstOrNull { it.first.contains(wanted) || wanted.contains(it.first) }
            ?: return@withContext "No encuentro ninguna app llamada «${args.str("app")}»."

        val intent = pm.getLaunchIntentForPackage(match.second)
            ?: return@withContext "No puedo abrir esa app."

        if (context.launch(intent)) "Abriendo ${pm.getApplicationLabel(pm.getApplicationInfo(match.second, 0))}."
        else "No he podido abrir la app."
    }
}

/** Pone una alarma en la app de reloj del móvil. */
class SetAlarmTool(private val context: Context) : Tool {
    override val name = "setAlarm"
    override val description = "Pone una alarma en el reloj del móvil a una hora (para despertarse, etc.)."
    override val direct = true
    override val parameters = Schema.obj(
        "hour" to Schema.integer("Hora (0-23)."),
        "minute" to Schema.integer("Minuto (0-59)."),
        "label" to Schema.string("Etiqueta opcional, ej: 'Gimnasio'."),
        required = listOf("hour", "minute")
    )

    override suspend fun execute(args: JSONObject): String {
        val hour = args.int("hour")?.takeIf { it in 0..23 } ?: return "La hora no es válida."
        val minute = (args.int("minute") ?: 0).coerceIn(0, 59)

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args.str("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }

        return if (context.launch(intent)) "Alarma puesta a las %d:%02d.".format(hour, minute)
        else "No he encontrado una app de reloj para poner la alarma."
    }
}

/** Temporizador en la app de reloj. */
class SetTimerTool(private val context: Context) : Tool {
    override val name = "setTimer"
    override val description = "Pone un temporizador ('pon un temporizador de 10 minutos')."
    override val direct = true
    override val parameters = Schema.obj(
        "seconds" to Schema.integer("Duración total en segundos."),
        "label" to Schema.string("Etiqueta opcional, ej: 'pasta'."),
        required = listOf("seconds")
    )

    override suspend fun execute(args: JSONObject): String {
        val seconds = args.int("seconds")?.takeIf { it in 1..86_400 } ?: return "La duración no es válida."

        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args.str("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }

        val text = when {
            seconds % 3600 == 0 -> "${seconds / 3600} h"
            seconds % 60 == 0 -> "${seconds / 60} min"
            else -> "$seconds s"
        }
        return if (context.launch(intent)) "Temporizador de $text en marcha." else "No he encontrado una app de reloj."
    }
}

/**
 * Pone una canción concreta. YouTube: busca el primer vídeo (sin API key) y lo
 * abre en la app de YouTube. Spotify: abre la búsqueda en la app de Spotify.
 */
class PlaySongTool(private val context: Context) : Tool {
    override val name = "playSong"
    override val description =
        "Busca y pone una canción, artista o álbum concreto ('pon...', 'quiero escuchar...'). " +
            "En YouTube por defecto; en Spotify solo si el usuario lo pide."
    override val direct = true
    override val parameters = Schema.obj(
        "query" to Schema.string("Canción, idealmente 'título artista'."),
        "platform" to Schema.string("Dónde ponerla.", listOf("youtube", "spotify")),
        required = listOf("query")
    )

    private val videoRegex = Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")
    private val titleRegex = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")

    override suspend fun execute(args: JSONObject): String = withContext(Dispatchers.IO) {
        val query = args.str("query") ?: return@withContext "¿Qué canción pongo?"
        val encoded = URLEncoder.encode(query, "UTF-8")

        if (args.str("platform") == "spotify") {
            val opened = context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${encoded.replace("+", "%20")}")))
            return@withContext if (opened) "He abierto «$query» en Spotify; dale a reproducir."
            else "No tienes Spotify instalado. ¿La pongo en YouTube?"
        }

        val searchUrl = "https://www.youtube.com/results?search_query=$encoded"
        val html = runCatching {
            Http.getText(
                searchUrl,
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36",
                    "Accept-Language" to "es-ES,es;q=0.9",
                    "Cookie" to "CONSENT=YES+cb; SOCS=CAI"
                )
            )
        }.getOrNull()

        val match = html?.let { videoRegex.find(it) }
        if (match == null) {
            return@withContext if (context.launch(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))) {
                "Te he abierto la búsqueda de «$query» en YouTube."
            } else {
                "No he podido abrir YouTube."
            }
        }

        val videoId = match.groupValues[1]
        val page = html!!
        val window = page.substring(match.range.last, minOf(page.length, match.range.last + 4000))
        val title = titleRegex.find(window)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")?.replace("\\\"", "\"") ?: query

        // La app de YouTube recoge este enlace; si no está instalada, se abre en el navegador.
        val opened = context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")))
        if (opened) "Poniendo «$title» en YouTube." else "No he podido abrir YouTube."
    }
}
