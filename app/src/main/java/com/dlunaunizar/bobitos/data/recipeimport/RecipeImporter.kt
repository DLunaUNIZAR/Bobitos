package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.Ingredient
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject

/** Importa una receta desde la URL de una web. */
interface RecipeImporter {
    /** @throws RecipeImportException si la URL es inválida, no descarga o no contiene una receta. */
    suspend fun import(url: String): ImportedRecipe
}

/**
 * Extrae la receta de los datos estructurados **schema.org/Recipe (JSON-LD)** incrustados en el HTML
 * servido, que casi todos los blogs/portales publican por SEO. No hace scraping por sitio ni ejecuta
 * JavaScript: si la web no expone JSON-LD (p. ej. un SPA), falla con [ImportFailure.NoRecipeFound].
 */
class JsonLdRecipeImporter @Inject constructor(private val fetcher: HtmlFetcher) : RecipeImporter {
    override suspend fun import(url: String): ImportedRecipe {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) throw RecipeImportException(ImportFailure.InvalidUrl)
        val html = fetcher.fetch(trimmed)
        val recipe = extractRecipe(html) ?: throw RecipeImportException(ImportFailure.NoRecipeFound)
        return recipe.toImportedRecipe(trimmed)
    }

    private fun extractRecipe(html: String): JSONObject? {
        val scripts = Jsoup.parse(html).select("script[type=application/ld+json]")
        for (script in scripts) {
            val root = runCatching { JSONTokener(script.data()).nextValue() }.getOrNull() ?: continue
            findRecipe(root)?.let { return it }
        }
        return null
    }

    // Localiza un nodo @type Recipe: objeto suelto, dentro de un @graph, o en un array de nodos.
    private fun findRecipe(node: Any?): JSONObject? = when (node) {
        is JSONObject -> when {
            node.hasType("Recipe") -> node
            else -> findRecipe(node.opt("@graph"))
        }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it)) }
        else -> null
    }

    private fun JSONObject.toImportedRecipe(sourceUrl: String): ImportedRecipe {
        val title = cleanText(optText("name")).take(MAX_TITLE)
        if (title.isBlank()) throw RecipeImportException(ImportFailure.NoRecipeFound)
        return ImportedRecipe(
            title = title,
            description = cleanText(optText("description")).takeIf(String::isNotBlank)?.take(MAX_DESCRIPTION),
            category = parseCategory()?.take(MAX_CATEGORY),
            ingredients = parseIngredients(),
            sourceUrl = sourceUrl,
            steps = RecipeMetaParser.steps(parseInstructions()),
            servings = RecipeMetaParser.servings(opt("recipeYield")),
            totalMinutes = RecipeMetaParser.totalMinutes(
                optText("totalTime"),
                optText("prepTime"),
                optText("cookTime"),
            ),
        )
    }

    private fun JSONObject.parseCategory(): String? {
        val category = when (val raw = opt("recipeCategory")) {
            is String -> raw
            is JSONArray -> raw.optText(0)
            else -> ""
        }
        return cleanText(category).takeIf(String::isNotBlank)
    }

    private fun JSONObject.parseInstructions(): List<String> = when (val raw = opt("recipeInstructions")) {
        is String -> splitInstructionText(raw)
        is JSONArray -> flattenInstructions(raw)
        else -> emptyList()
    }

    // recipeInstructions puede ser texto, lista de strings, de HowToStep {text} o de HowToSection; cada texto puede traer varios pasos.
    private fun flattenInstructions(array: JSONArray): List<String> {
        val steps = mutableListOf<String>()
        for (index in 0 until array.length()) {
            when (val item = array.opt(index)) {
                is String -> steps += splitInstructionText(item)
                is JSONObject -> {
                    val section = item.opt("itemListElement")
                    if (section is JSONArray) {
                        steps += flattenInstructions(section)
                    } else {
                        steps += splitInstructionText(item.optText("text").ifBlank { item.optText("name") })
                    }
                }
            }
        }
        return steps
    }

    // Parte un texto de instrucciones en pasos. Las etiquetas escapadas («&lt;br&gt;») se leen como etiquetas. Separan
    // pasos <br>, <p>, </p>, <li> y </li>, y también los saltos de línea, salvo en HTML con párrafos o listas, donde
    // solo son el formato del código fuente.
    private fun splitInstructionText(raw: String): List<String> {
        val html = if (ESCAPED_TAG.containsMatchIn(raw)) Parser.unescapeEntities(raw, false) else raw
        val text = if (HTML_BLOCK.containsMatchIn(html)) html.replace(SOURCE_LINE_BREAK, " ") else html
        return text.replace(HTML_LINE_BREAK, "\n").lines().map { cleanText(it) }.filter(String::isNotBlank)
    }

    private fun JSONObject.parseIngredients(): List<Ingredient> {
        val array = (opt("recipeIngredient") ?: opt("ingredients")) as? JSONArray ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { IngredientParser.parse(cleanText(array.optText(it))) }
            .take(MAX_INGREDIENTS)
    }

    private fun JSONObject.hasType(type: String): Boolean = when (val raw = opt("@type")) {
        is String -> raw.equals(type, ignoreCase = true)
        is JSONArray -> (0 until raw.length()).any { raw.optText(it).equals(type, ignoreCase = true) }
        else -> false
    }

    // Decodifica entidades HTML y elimina posibles etiquetas de los valores de texto.
    private fun cleanText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return Jsoup.parse(raw).text().trim()
    }

    private companion object {
        val HTML_LINE_BREAK = Regex("<br\\s*/?>|</?(?:p|li)\\b[^>]*>", RegexOption.IGNORE_CASE)
        val HTML_BLOCK = Regex("</?(?:p|li)\\b", RegexOption.IGNORE_CASE)
        val ESCAPED_TAG = Regex("&lt;\\s*/?\\s*(?:br|p|li)\\b", RegexOption.IGNORE_CASE)
        val SOURCE_LINE_BREAK = Regex("\\r?\\n")
        const val MAX_TITLE = 120
        const val MAX_DESCRIPTION = 1000
        const val MAX_CATEGORY = 60
        const val MAX_INGREDIENTS = 50
    }
}

// No se usa optString: en Android devuelve el texto "null" ante un null del JSON (el org.json de la JVM, "").
internal fun JSONObject.optText(key: String): String = (opt(key) as? String).orEmpty()

internal fun JSONArray.optText(index: Int): String = (opt(index) as? String).orEmpty()
