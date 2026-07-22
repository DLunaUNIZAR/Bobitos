package com.dlunaunizar.bobitos.core.navigation

/**
 * Extrae el enlace de receta de lo que llega al compartir texto desde el navegador. El texto puede
 * ser solo la URL o llevarla dentro de una frase; nos quedamos con la primera URL https (única que
 * la app puede descargar, ya que el tráfico en claro está deshabilitado).
 */
object RecipeShareUrl {
    private val HTTPS_URL = Regex("https://[^\\s]+", RegexOption.IGNORE_CASE)

    fun from(sharedText: String?): String? {
        if (sharedText.isNullOrBlank()) return null
        val match = HTTPS_URL.find(sharedText)?.value ?: return null
        // Recorta puntuación de cierre que suele quedar pegada al final de una frase.
        return match.trimEnd('.', ',', ')', ']', '}', '"', '\'', '>')
    }
}
