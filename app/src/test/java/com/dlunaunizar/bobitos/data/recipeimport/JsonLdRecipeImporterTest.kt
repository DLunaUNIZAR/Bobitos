package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.Ingredient
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonLdRecipeImporterTest {
    private val fetcher = FakeHtmlFetcher()
    private val importer = JsonLdRecipeImporter(fetcher)

    @Test
    fun `maps a plain Recipe node`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@context": "https://schema.org",
              "@type": "Recipe",
              "name": "Tortilla de patatas",
              "recipeCategory": "Plato principal",
              "recipeIngredient": ["4 huevos", "300 g de patatas", "Sal al gusto"],
              "recipeInstructions": ["Pelar las patatas", "Batir los huevos", "Cuajar la tortilla"]
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/tortilla")

        assertEquals("Tortilla de patatas", recipe.title)
        assertEquals("Plato principal", recipe.category)
        assertEquals(null, recipe.description)
        assertEquals(listOf("Pelar las patatas", "Batir los huevos", "Cuajar la tortilla"), recipe.steps)
        assertEquals(
            listOf(
                Ingredient("huevos", "4", null),
                Ingredient("patatas", "300", "g"),
                Ingredient("Sal al gusto", null, null),
            ),
            recipe.ingredients,
        )
        assertEquals("https://example.com/tortilla", recipe.sourceUrl)
    }

    @Test
    fun `finds a Recipe inside a graph`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@context": "https://schema.org",
              "@graph": [
                { "@type": "WebSite", "name": "Blog" },
                { "@type": "Recipe", "name": "Gazpacho", "recipeIngredient": ["1 kg de tomate"] }
              ]
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/gazpacho")

        assertEquals("Gazpacho", recipe.title)
        assertEquals(listOf(Ingredient("tomate", "1", "kg")), recipe.ingredients)
    }

    @Test
    fun `accepts an array type that includes Recipe`() = runTest {
        fetcher.html = pageWith(
            """
            { "@type": ["Recipe", "NewsArticle"], "name": "Lentejas" }
            """.trimIndent(),
        )

        assertEquals("Lentejas", importer.import("https://example.com/lentejas").title)
    }

    @Test
    fun `reads HowToStep instructions`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Bizcocho",
              "recipeInstructions": [
                { "@type": "HowToStep", "text": "Mezclar los secos" },
                { "@type": "HowToStep", "text": "Añadir los huevos" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Mezclar los secos", "Añadir los huevos"), importer.import("https://example.com/b").steps)
    }

    @Test
    fun `keeps the description when there are no instructions`() = runTest {
        fetcher.html = pageWith(
            """
            { "@type": "Recipe", "name": "Café", "description": "Un café rápido" }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/cafe")

        assertEquals("Un café rápido", recipe.description)
        assertEquals(emptyList<String>(), recipe.steps)
        assertEquals(null, recipe.servings)
        assertEquals(null, recipe.totalMinutes)
    }

    @Test
    fun `keeps the web description as the intro and reads steps, servings and time`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Lentejas",
              "description": "Las de toda la vida.",
              "recipeYield": ["4", "4 raciones"],
              "totalTime": "PT1H",
              "recipeInstructions": [
                { "@type": "HowToSection", "name": "Preparación", "itemListElement": [
                  { "@type": "HowToStep", "text": "Remoja las lentejas." },
                  { "@type": "HowToStep", "text": "Cuécelas con la verdura." }
                ] }
              ]
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/lentejas")

        assertEquals("Las de toda la vida.", recipe.description)
        assertEquals(listOf("Remoja las lentejas.", "Cuécelas con la verdura."), recipe.steps)
        assertEquals(4, recipe.servings)
        assertEquals(60, recipe.totalMinutes)
    }

    @Test
    fun `a single instructions text is split by its line breaks`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Gazpacho",
              "recipeInstructions": "Trocea las verduras.\nTritúralo todo.<br>Enfríalo en la nevera.",
              "prepTime": "PT15M",
              "cookTime": "PT0M",
              "recipeYield": "Para 6 personas"
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/gazpacho")

        assertEquals(listOf("Trocea las verduras.", "Tritúralo todo.", "Enfríalo en la nevera."), recipe.steps)
        assertEquals(6, recipe.servings)
        assertEquals(15, recipe.totalMinutes)
    }

    @Test
    fun `decodes html entities in fields`() = runTest {
        fetcher.html = pageWith(
            """
            { "@type": "Recipe", "name": "Arroz &amp; verduras" }
            """.trimIndent(),
        )

        assertEquals("Arroz & verduras", importer.import("https://example.com/a").title)
    }

    @Test
    fun `fails when there is no json-ld`() {
        fetcher.html = "<html><body><h1>Sin datos</h1></body></html>"

        val error = assertThrows(RecipeImportException::class.java) {
            runTest { importer.import("https://example.com/none") }
        }
        assertEquals(ImportFailure.NoRecipeFound, error.failure)
    }

    @Test
    fun `fails when the recipe has no name`() {
        fetcher.html = pageWith("""{ "@type": "Recipe", "recipeIngredient": ["1 huevo"] }""")

        val error = assertThrows(RecipeImportException::class.java) {
            runTest { importer.import("https://example.com/anon") }
        }
        assertEquals(ImportFailure.NoRecipeFound, error.failure)
    }

    @Test
    fun `fails on a blank url before fetching`() {
        val error = assertThrows(RecipeImportException::class.java) {
            runTest { importer.import("   ") }
        }
        assertEquals(ImportFailure.InvalidUrl, error.failure)
    }

    @Test
    fun `optText reads only real strings and never returns the text null`() {
        val obj = JSONObject("""{"description": null, "name": "Lentejas", "yield": 4}""")
        assertEquals("", obj.optText("description"))
        assertEquals("Lentejas", obj.optText("name"))
        assertEquals("", obj.optText("yield"))
        assertEquals("", obj.optText("falta"))

        val array = JSONArray("""[null, "Lentejas", 4]""")
        assertEquals("", array.optText(0))
        assertEquals("Lentejas", array.optText(1))
        assertEquals("", array.optText(2))
        assertEquals("", array.optText(5))
    }

    @Test
    fun `a null in the page does not become the text null`() = runTest {
        fetcher.html = pageWith(
            """
            {
              "@type": "Recipe",
              "name": "Lentejas",
              "description": null,
              "recipeIngredient": ["200 g de lentejas", null],
              "recipeInstructions": [
                { "@type": "HowToStep", "text": null },
                { "@type": "HowToStep", "text": "Cocer las lentejas" }
              ]
            }
            """.trimIndent(),
        )

        val recipe = importer.import("https://example.com/lentejas")

        assertEquals(null, recipe.description)
        assertEquals(listOf("Cocer las lentejas"), recipe.steps)
        assertEquals(listOf(Ingredient("lentejas", "200", "g")), recipe.ingredients)
    }

    private fun pageWith(jsonLd: String): String =
        """<html><head><script type="application/ld+json">$jsonLd</script></head><body></body></html>"""
}

private class FakeHtmlFetcher : HtmlFetcher {
    var html: String = ""
    override suspend fun fetch(url: String): String = html
}
