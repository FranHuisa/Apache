package com.apache.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.apache.network.saveImageInCore
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.model.ChatImage
import com.apache.network.downloadImageBitmap
import com.apache.ui.theme.ApacheColors
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fila de 1 a 3 imágenes que acompaña a una respuesta de Apache.
 *
 * Con una sola imagen se dibuja más grande; con 2-3 se reparten en
 * miniaturas del mismo tamaño. Al hacer clic se abre la página de origen
 * (o la propia imagen) en el navegador.
 */
@Composable
fun ChatImages(images: List<ChatImage>) {
    val visible = images.take(3)
    if (visible.isEmpty()) return

    val (width, height) = if (visible.size == 1) 380.dp to 260.dp else 230.dp to 170.dp

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        visible.forEachIndexed { index, image -> ChatImageCard(image, index + 1, width, height) }
    }
}

/** Estado del guardado de una imagen (solo se guarda si el usuario hace clic). */
private sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Saved(val path: String) : SaveState()
    data class Failed(val message: String) : SaveState()
}

/** Estado de carga de una imagen del chat. */
private sealed class ImageLoadState {
    object Loading : ImageLoadState()
    data class Loaded(val bitmap: ImageBitmap) : ImageLoadState()
    object Failed : ImageLoadState()
}

@Composable
private fun ChatImageCard(image: ChatImage, position: Int, width: Dp, height: Dp) {

    val scope = rememberCoroutineScope()
    var saveState by remember(image.url) { mutableStateOf<SaveState>(SaveState.Idle) }

    // Clic en la imagen = guardarla en Imágenes/Apache (las imágenes no se guardan solas).
    fun save() {
        if (saveState is SaveState.Saving || saveState is SaveState.Saved) return
        saveState = SaveState.Saving
        scope.launch {
            saveState = try {
                SaveState.Saved(withContext(Dispatchers.IO) { saveImageInCore(image) })
            } catch (e: Exception) {
                SaveState.Failed(e.message ?: "No se ha podido guardar")
            }
        }
    }

    val state by produceState<ImageLoadState>(ImageLoadState.Loading, image.url) {
        value = try {
            ImageLoadState.Loaded(withContext(Dispatchers.IO) { downloadImageBitmap(image.url) })
        } catch (_: Exception) {
            ImageLoadState.Failed
        }
    }

    Column(modifier = Modifier.width(width)) {
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(RoundedCornerShape(12.dp))
                .background(ApacheColors.botBubble)
                .clickable { save() },
            contentAlignment = Alignment.Center
        ) {
            when (val current = state) {
                is ImageLoadState.Loading ->
                    CircularProgressIndicator(color = ApacheColors.accent, modifier = Modifier.size(28.dp))

                is ImageLoadState.Loaded ->
                    Image(
                        bitmap = current.bitmap,
                        contentDescription = image.title.ifBlank { "Imagen" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                is ImageLoadState.Failed ->
                    Text(
                        text = "No se ha podido cargar la imagen",
                        color = ApacheColors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
            }

            // Número de la imagen, para poder decir "guarda la 2".
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color(0xAA000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = position.toString(), color = Color.White, fontSize = 12.sp)
            }
        }

        // Estado del guardado / pista.
        val (statusText, statusColor) = when (val current = saveState) {
            is SaveState.Idle -> "Clic para guardar" to ApacheColors.textFaint
            is SaveState.Saving -> "Guardando..." to ApacheColors.textMuted
            is SaveState.Saved -> "✓ Guardada en Imágenes/Apache" to ApacheColors.accentLight
            is SaveState.Failed -> current.message to ApacheColors.dangerSoft
        }

        Row(
            modifier = Modifier.width(width).padding(top = 4.dp, start = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Origen ↗",
                color = ApacheColors.accentLight,
                fontSize = 11.sp,
                modifier = Modifier.clickable { openInBrowser(image.sourceUrl ?: image.url) }
            )
        }

        if (image.title.isNotBlank()) {
            Text(
                text = image.title,
                color = ApacheColors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp)
            )
        }
    }
}

/** Abre una URL en el navegador por defecto del sistema, si está disponible. */
private fun openInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {
        // Si no se puede abrir el navegador simplemente no hacemos nada.
    }
}
