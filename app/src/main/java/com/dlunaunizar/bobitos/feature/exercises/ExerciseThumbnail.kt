package com.dlunaunizar.bobitos.feature.exercises

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.data.repository.LoadedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ThumbnailSize = 40.dp
private val ThumbnailCorner = 10.dp

/**
 * La mayor potencia de 2 que deja el lado menor de una imagen de [width] x [height] por encima o igual que
 * [targetPx], y como mínimo 1: el `inSampleSize` con el que decodificarla sin pasarse de tamaño.
 */
internal fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    val shortSide = minOf(width, height)
    var sample = 1
    while (shortSide / (sample * 2) >= targetPx) sample *= 2
    return sample
}

// Decodifica en el hilo de fondo leyendo antes los límites, para no subir a memoria la imagen a tamaño completo.
private fun decodeThumbnail(bytes: ByteArray, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/**
 * Miniatura cuadrada de 40 dp de un ejercicio en las listas. Mientras carga, y si no hay imagen, enseña el icono del
 * tipo. Es decorativa: el nombre ya lo dice la fila.
 */
@Composable
internal fun ExerciseThumbnail(
    exercise: CatalogExercise,
    load: suspend () -> LoadedImage?,
    modifier: Modifier = Modifier,
) {
    val targetPx = with(LocalDensity.current) { ThumbnailSize.roundToPx() }
    val image = exercise.image
    val bitmap by produceState<ImageBitmap?>(initialValue = null, exercise.id, image?.hash, targetPx) {
        value = if (image == null) {
            null
        } else {
            load()?.let { loaded -> withContext(Dispatchers.Default) { decodeThumbnail(loaded.bytes, targetPx) } }
        }
    }
    Box(
        modifier = modifier
            .size(ThumbnailSize)
            .clip(RoundedCornerShape(ThumbnailCorner))
            .background(
                if (bitmap !=
                    null
                ) {
                    exerciseImageBackground(LocalDarkTheme.current)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(ThumbnailSize),
            )
        } ?: Icon(
            imageVector = exercise.type.icon,
            contentDescription = null,
            tint = exercise.type.accent(),
            modifier = Modifier.size(24.dp),
        )
    }
}
