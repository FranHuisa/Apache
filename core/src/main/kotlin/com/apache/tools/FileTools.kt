package com.apache.tools

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Component

/**
 * Carpetas personales del usuario, resolviendo los nombres reales en Windows:
 * pueden estar dentro de OneDrive y con nombre en español (Escritorio,
 * Documentos, Imágenes...).
 */
object UserFolders {

    private val home = File(System.getProperty("user.home"))

    private val candidates: Map<String, List<String>> = mapOf(
        "escritorio" to listOf("Desktop", "Escritorio", "OneDrive/Desktop", "OneDrive/Escritorio"),
        "documentos" to listOf("Documents", "Documentos", "OneDrive/Documents", "OneDrive/Documentos"),
        "descargas" to listOf("Downloads", "Descargas"),
        "imagenes" to listOf("Pictures", "Imágenes", "OneDrive/Pictures", "OneDrive/Imágenes"),
        "musica" to listOf("Music", "Música"),
        "videos" to listOf("Videos", "Vídeos")
    )

    val names: List<String> = candidates.keys.toList() + "todas"

    /** Carpetas reales que existen para un nombre ("descargas"...) o todas si es "todas"/desconocido. */
    fun resolve(name: String?): List<File> {
        val key = name?.let(::normalize)
        val keys = if (key == null || key !in candidates) candidates.keys else setOf(key)

        return keys.flatMap { k -> candidates.getValue(k).map { File(home, it) } }
            .filter { it.isDirectory }
            .distinctBy { it.canonicalPath }
    }
}

/** Minúsculas y sin tildes, para comparar nombres ("Canción" == "cancion"). */
internal fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Carpetas que no tiene sentido recorrer (enormes o internas). */
private val SKIPPED_DIRS = setOf("node_modules", ".git", ".gradle", "build", "appdata", "\$recycle.bin", ".idea")

private const val MAX_VISITED = 60_000
private const val MAX_DEPTH = 6

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun File.describe(): String {
    val modified = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified()), ZoneId.systemDefault())
    val size = when {
        length() >= 1_048_576 -> "%.1f MB".format(length() / 1_048_576.0)
        length() >= 1024 -> "${length() / 1024} KB"
        else -> "${length()} B"
    }
    return "$absolutePath · $size · ${modified.format(DATE_FORMAT)}"
}

/**
 * Recorre las carpetas dadas (hasta [MAX_DEPTH] niveles y [MAX_VISITED]
 * archivos, para que una búsqueda nunca se quede colgada) y devuelve los
 * archivos que cumplan [accept].
 */
private fun walkFiles(roots: List<File>, accept: (File) -> Boolean): List<File> {
    val found = mutableListOf<File>()
    var visited = 0

    roots.forEach { root ->
        root.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .onEnter { dir -> !dir.isHidden && normalize(dir.name) !in SKIPPED_DIRS }
            .forEach { file ->
                if (visited++ > MAX_VISITED) return found
                if (file.isFile && !file.isHidden && accept(file)) found.add(file)
            }
    }
    return found
}

private fun extensionFilter(raw: Any?): Set<String> =
    (raw as? String).orEmpty()
        .split(',', ' ', ';')
        .map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotBlank() }
        .toSet()

private fun limitArg(raw: Any?, default: Int): Int =
    ((raw as? Number)?.toInt() ?: (raw as? String)?.toIntOrNull() ?: default).coerceIn(1, 30)

/** Busca archivos por nombre en las carpetas personales del usuario. */
@Component
class SearchFilesTool : Tool {

    override val name = "searchFiles"

    override val description =
        "Busca archivos en las carpetas personales del usuario (escritorio, documentos, descargas, " +
            "imágenes, música, vídeos) por parte de su nombre y, opcionalmente, por extensión. " +
            "Devuelve las rutas completas, ordenadas de más reciente a más antiguo. Úsalo antes de " +
            "openFile cuando el usuario pida abrir o encontrar un archivo."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Parte del nombre del archivo, ej: 'factura', 'cv', 'tfg'. Puede ir vacío si se busca solo por extensión."
            ),
            "folder" to mapOf(
                "type" to "string",
                "description" to "Dónde buscar. Por defecto 'todas'.",
                "enum" to UserFolders.names
            ),
            "extensions" to mapOf(
                "type" to "string",
                "description" to "Extensiones separadas por comas, sin punto, ej: 'pdf' o 'jpg,png'. Opcional."
            ),
            "limit" to mapOf(
                "type" to "integer",
                "description" to "Máximo de resultados (1-30). Por defecto 10."
            )
        )
    )

    override fun execute(args: Map<String, Any?>): String {
        val query = normalize((args["query"] as? String).orEmpty())
        val extensions = extensionFilter(args["extensions"])
        val limit = limitArg(args["limit"], 10)

        if (query.isBlank() && extensions.isEmpty()) {
            return "Dime al menos parte del nombre o el tipo de archivo que buscas."
        }

        val roots = UserFolders.resolve(args["folder"] as? String)
        if (roots.isEmpty()) return "No he encontrado las carpetas personales del usuario."

        val results = walkFiles(roots) { file ->
            (query.isBlank() || normalize(file.name).contains(query)) &&
                (extensions.isEmpty() || file.extension.lowercase() in extensions)
        }
            .sortedByDescending { it.lastModified() }
            .take(limit)

        if (results.isEmpty()) {
            return "No he encontrado archivos que coincidan en ${roots.joinToString { it.name }}."
        }

        return "Archivos encontrados (ruta · tamaño · última modificación):\n" +
            results.joinToString("\n") { "- ${it.describe()}" }
    }
}

/** Lista los archivos modificados más recientemente (ej. "lo último que he descargado"). */
@Component
class RecentFilesTool : Tool {

    override val name = "recentFiles"

    override val description =
        "Lista los archivos más recientes de una carpeta personal (por defecto, descargas). Útil " +
            "para 'ábreme lo último que he descargado' o '¿qué PDFs tengo recientes?'."

    override val riskLevel = RiskLevel.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "folder" to mapOf(
                "type" to "string",
                "description" to "Carpeta. Por defecto 'descargas'.",
                "enum" to UserFolders.names
            ),
            "extensions" to mapOf(
                "type" to "string",
                "description" to "Extensiones separadas por comas, sin punto. Opcional."
            ),
            "limit" to mapOf(
                "type" to "integer",
                "description" to "Máximo de resultados (1-30). Por defecto 10."
            )
        )
    )

    override fun execute(args: Map<String, Any?>): String {
        val extensions = extensionFilter(args["extensions"])
        val limit = limitArg(args["limit"], 10)
        val roots = UserFolders.resolve((args["folder"] as? String) ?: "descargas")

        if (roots.isEmpty()) return "No he encontrado esa carpeta."

        val results = walkFiles(roots) { file ->
            extensions.isEmpty() || file.extension.lowercase() in extensions
        }
            .sortedByDescending { it.lastModified() }
            .take(limit)

        if (results.isEmpty()) return "No hay archivos recientes en ${roots.joinToString { it.name }}."

        return "Archivos más recientes (ruta · tamaño · última modificación):\n" +
            results.joinToString("\n") { "- ${it.describe()}" }
    }
}

/** Abre un archivo con su programa predeterminado, o muestra dónde está. */
@Component
class OpenFileTool : Tool {

    override val name = "openFile"

    override val description =
        "Abre un archivo o carpeta del ordenador con su programa predeterminado, o lo muestra " +
            "seleccionado en el explorador. Necesita la ruta completa: obtenla antes con " +
            "searchFiles o recentFiles. No inventes rutas."

    override val riskLevel = RiskLevel.REVERSIBLE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "Ruta completa del archivo o carpeta."
            ),
            "showInFolder" to mapOf(
                "type" to "boolean",
                "description" to "true para mostrarlo en el explorador en vez de abrirlo. Por defecto false."
            )
        ),
        "required" to listOf("path")
    )

    override fun execute(args: Map<String, Any?>): String {
        val path = (args["path"] as? String)?.trim()
        if (path.isNullOrBlank()) return "Falta la ruta del archivo."

        val file = File(path)
        if (!file.exists()) return "No existe '$path'. Búscalo primero con searchFiles."

        val showInFolder = args["showInFolder"] == true || (args["showInFolder"] as? String) == "true"

        if (showInFolder) {
            return if (SystemOpener.showInFolder(file)) {
                "Mostrando ${file.name} en el explorador."
            } else {
                "No he podido abrir el explorador."
            }
        }

        // No abrimos ejecutables ni scripts: abrir un archivo no debe poder ejecutar código.
        if (file.isFile && file.extension.lowercase() in BLOCKED_EXTENSIONS) {
            return "Por seguridad no abro archivos .${file.extension} desde Apache, pero puedo " +
                "mostrártelo en su carpeta."
        }

        return if (SystemOpener.open(file.absolutePath)) "Abriendo ${file.name}." else "No he podido abrir ${file.name}."
    }

    private companion object {
        val BLOCKED_EXTENSIONS = setOf("exe", "bat", "cmd", "ps1", "vbs", "msi", "scr", "com", "jar", "js", "reg", "lnk")
    }
}
