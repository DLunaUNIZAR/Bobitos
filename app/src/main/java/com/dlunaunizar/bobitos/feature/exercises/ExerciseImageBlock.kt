package com.dlunaunizar.bobitos.feature.exercises

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.rememberSafeLinks
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.ExerciseImage

private const val UNKNOWN_AUTHOR = "colaboradores de wger"
private val MaxImageHeight = 240.dp

// Crédito de una imagen: autor (o «colaboradores de wger»), etiqueta de licencia y su texto legal si se conoce.
internal data class ImageCredit(val author: String, val licenseLabel: String, val licenseUrl: String?)

internal fun ExerciseImage.credit(): ImageCredit = ImageCredit(
    author = author?.takeIf { it.isNotBlank() } ?: UNKNOWN_AUTHOR,
    licenseLabel = licenseLabel(license),
    licenseUrl = licenseUrl(license),
)

// Imagen de wger con su atribución. Fondo blanco fijo (los PNG transparentes se ven mal en tema oscuro).
// Si la carga falla se oculta todo el bloque, crédito incluido: no se atribuye lo que no se muestra.
@Composable
internal fun ExerciseImageBlock(image: ExerciseImage, exerciseName: String) {
    var failedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    if (failedUrl == image.url) return
    val credit = image.credit()
    val link = rememberSafeLinks()
    val creditPrefix = stringResource(R.string.exercises_image_credit, credit.author)
    Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = stringResource(R.string.exercises_image_description, exerciseName),
                contentScale = ContentScale.Fit,
                onError = { failedUrl = image.url },
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
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
