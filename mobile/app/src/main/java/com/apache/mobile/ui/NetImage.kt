package com.apache.mobile.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apache.mobile.tools.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Caché de imágenes ya descargadas (unas 30 miniaturas como mucho). */
private val cache = LruCache<String, ImageBitmap>(30)

/** Imagen de internet con indicador de carga y aviso si falla. */
@Composable
fun NetImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(cache.get(url), url) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val (bytes, _) = Http.getBytes(url, maxBytes = 5_000_000)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }?.also { cache.put(url, it) }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ApacheColors.accent)
        }
    }
}

@Composable
fun SmallNote(text: String) {
    Text(text = text, color = ApacheColors.textFaint, fontSize = 11.sp)
}
