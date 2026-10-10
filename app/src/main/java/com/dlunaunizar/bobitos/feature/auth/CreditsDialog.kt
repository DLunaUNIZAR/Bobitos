package com.dlunaunizar.bobitos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.rememberSafeLinks
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.feature.exercises.CC_BY_SA_4_URL
import com.dlunaunizar.bobitos.feature.exercises.WGER_URL

private const val OFF_URL = "https://world.openfoodfacts.org/"
private const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
private const val DBCL_URL = "https://opendatacommons.org/licenses/dbcl/1-0/"
private const val OFL_URL = "https://openfontlicense.org/open-font-license-official-text/"

// «Créditos y licencias»: avisos que exigen las licencias de los datos y la tipografía incluidos en la app.
@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val link = rememberSafeLinks()
    val wger = stringResource(R.string.credits_wger_body)
    val off = stringResource(R.string.credits_off_body)
    val font = stringResource(R.string.credits_font_body)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.credits_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CreditBlock(
                    stringResource(R.string.credits_wger_title),
                    linked(wger, link, "wger.de" to WGER_URL, "CC BY-SA 4.0" to CC_BY_SA_4_URL),
                )
                CreditBlock(
                    stringResource(R.string.credits_off_title),
                    linked(off, link, "Open Food Facts" to OFF_URL, "ODbL 1.0" to ODBL_URL, "DbCL 1.0" to DBCL_URL),
                )
                CreditBlock(
                    stringResource(R.string.credits_font_title),
                    linked(font, link, "SIL Open Font License 1.1" to OFL_URL),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )
}

@Composable
private fun CreditBlock(title: String, body: AnnotatedString) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

// Convierte en enlace la primera aparición de cada fragmento indicado; el resto del texto queda tal cual.
private fun linked(
    text: String,
    link: (String) -> LinkAnnotation.Url,
    vararg targets: Pair<String, String>,
): AnnotatedString = buildAnnotatedString {
    val spans = targets.mapNotNull { (needle, url) ->
        text.indexOf(needle).takeIf { it >= 0 }?.let { Triple(it, needle, url) }
    }.sortedBy { it.first }
    var cursor = 0
    for ((start, needle, url) in spans) {
        if (start < cursor) continue
        append(text.substring(cursor, start))
        withLink(link(url)) { append(needle) }
        cursor = start + needle.length
    }
    append(text.substring(cursor))
}
