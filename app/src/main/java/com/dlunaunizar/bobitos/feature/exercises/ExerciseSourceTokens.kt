package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.ExerciseSource

// Fuente única de los enlaces de wger y de la licencia de sus fichas (también los usa «Créditos»).
internal const val WGER_URL = "https://wger.de/"
internal const val CC_BY_SA_4_URL = "https://creativecommons.org/licenses/by-sa/4.0/deed.es"
private const val CC = "https://creativecommons.org"

// Código de licencia guardado en la ficha → etiqueta legible. Un código desconocido se muestra tal cual.
internal fun licenseLabel(code: String): String = when (code) {
    "CC-BY-SA-4.0" -> "CC BY-SA 4.0"
    "CC-BY-SA-3.0" -> "CC BY-SA 3.0"
    "CC-BY-4.0" -> "CC BY 4.0"
    "CC0-1.0" -> "CC0 1.0"
    else -> code
}

// Texto legal de la licencia en español (Creative Commons); null si el código no se conoce.
internal fun licenseUrl(code: String): String? = when (code) {
    "CC-BY-SA-4.0" -> CC_BY_SA_4_URL
    "CC-BY-SA-3.0" -> "$CC/licenses/by-sa/3.0/deed.es"
    "CC-BY-4.0" -> "$CC/licenses/by/4.0/deed.es"
    "CC0-1.0" -> "$CC/publicdomain/zero/1.0/deed.es"
    else -> null
}

// Solo se enlaza la ficha de wger.de; cualquier otra URL (aunque venga de Firestore) se muestra sin enlace.
internal fun ExerciseSource.linkUrl(): String? = url?.takeIf { it.startsWith(WGER_URL) }

// Qué atribución mostrar según el proveedor de la ficha; el texto lo compone la UI.
internal sealed interface ExerciseAttribution {
    data class Wger(val sourceId: Long, val author: String?, val license: String, val url: String?) :
        ExerciseAttribution

    // Fichas propias del catálogo: texto original del proyecto, sin ficha externa que enlazar.
    data class Bobitos(val license: String) : ExerciseAttribution

    data class Other(val provider: String, val license: String) : ExerciseAttribution
}

internal fun ExerciseSource.attribution(): ExerciseAttribution = when (provider) {
    "wger" -> ExerciseAttribution.Wger(sourceId, author, license, linkUrl())
    "bobitos" -> ExerciseAttribution.Bobitos(license)
    else -> ExerciseAttribution.Other(provider, license)
}
