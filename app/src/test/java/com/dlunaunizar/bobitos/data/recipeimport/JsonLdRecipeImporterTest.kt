package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.Ingredient
import kotlinx.coroutines.test.runTest
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
        assertEquals("Pelar las patatas\nBatir los huevos\nCuajar la tortilla", recipe.description)
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

        assertEquals("Mezclar los secos\nAñadir los huevos", importer.import("https://example.com/b").description)
    }

    @Test
    fun `falls back to description when there are no instructions`() = runTest {
        fetcher.html = pageWith(
            """
            { "@type": "Recipe", "name": "Café", "description": "Un café rápido" }
            """.trimIndent(),
        )

        assertEquals("Un café rápido", importer.import("https://example.com/cafe").description)
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

    private fun pageWith(jsonLd: String): String =
        """<html><head><script type="application/ld+json">$jsonLd</script></head><body></body></html>"""
}

private class FakeHtmlFetcher : HtmlFetcher {
    var html: String = ""
    override suspend fun fetch(url: String): String = html
}
