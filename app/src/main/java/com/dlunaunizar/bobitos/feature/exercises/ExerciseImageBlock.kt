package com.dlunaunizar.bobitos.feature.exercises

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.rememberSafeLinks
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.ExerciseImage
import com.dlunaunizar.bobitos.data.repository.LoadedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UNKNOWN_AUTHOR = "colaboradores de wger"
private val MaxImageHeight = 240.dp

// Crédito de una imagen: autor (o «colaboradores de wger»), etiqueta de licencia y su texto legal si se conoce.
internal data class ImageCredit(val author: String, val licenseLabel: String, val licenseUrl: String?)

// Sale del documento de imagen que se muestra; la licencia de la ficha solo es reserva si el documento no la trae.
internal fun LoadedImage.credit(fallbackLicense: String): ImageCredit {
    val code = license?.takeIf { it.isNotBlank() } ?: fallbackLicense
    return ImageCredit(
        author = author?.takeIf { it.isNotBlank() } ?: UNKNOWN_AUTHOR,
        licenseLabel = licenseLabel(code),
        licenseUrl = licenseUrl(code),
    )
}

// Imagen de la ficha leída de Firestore, con su atribución. Fondo blanco fijo (los WebP pueden ser transparentes).
// Si no hay bitmap (sin red, sin documento, bytes corruptos) no se pinta nada, crédito incluido: no se atribuye lo que no se muestra.
@Composable
internal fun ExerciseImageBlock(image: ExerciseImage, exerciseName: String, load: suspend () -> LoadedImage?) {
    val loaded by produceState<Pair<ImageBitmap, ImageCredit>?>(initialValue = null, image.hash) {
        value = load()?.let { img ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)?.asImageBitmap()
            }?.let { it to img.credit(image.license) }
        }
    }
    val (shown, credit) = loaded ?: return
    val link = rememberSafeLinks()
    val creditPrefix = stringResource(R.string.exercises_image_credit, credit.author)
    val creditSuffix = stringResource(R.string.exercises_image_credit_suffix)
    Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            Image(
                bitmap = shown,
                contentDescription = stringResource(R.string.exercises_image_description, exerciseName),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = MaxImageHeight),
            )
        }
        Text(
            text = buildAnnotatedString {
                append(creditPrefix)
                credit.licenseUrl?.let { withLink(link(it)) { append(credit.licenseLabel) } }
                    ?: append(credit.licenseLabel)
                append(creditSuffix)
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
