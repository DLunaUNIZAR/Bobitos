package com.dlunaunizar.bobitos.data.recipeimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

/** Descarga el HTML de una URL. Aislado tras una interfaz para poder testear el parseo con fixtures. */
interface HtmlFetcher {
    suspend fun fetch(url: String): String
}

/**
 * Descarga con [HttpURLConnection] (sin dependencias de red extra). Solo https, con timeouts, tope de
 * tamaño y verificación de que la respuesta sea HTML. Los fallos se traducen a [RecipeImportException].
 */
class HttpUrlHtmlFetcher @Inject constructor() : HtmlFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val parsed = try {
            URL(url)
        } catch (error: MalformedURLException) {
            throw RecipeImportException(ImportFailure.InvalidUrl, error)
        }
        if (!parsed.protocol.equals("https", ignoreCase = true)) {
            throw RecipeImportException(ImportFailure.InvalidUrl)
        }
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            if (connection.responseCode !in HTTP_OK_RANGE) {
                throw RecipeImportException(ImportFailure.Network)
            }
            val contentType = connection.contentType.orEmpty()
            if (contentType.isNotEmpty() && !contentType.contains("html", ignoreCase = true)) {
                throw RecipeImportException(ImportFailure.NotHtml)
            }
            connection.inputStream.readBoundedText()
        } catch (error: IOException) {
            throw RecipeImportException(ImportFailure.Network, error)
        } finally {
            connection.disconnect()
        }
    }

    // Lee como UTF-8 hasta el tope; si lo supera, aborta con TooLarge en vez de agotar la memoria.
    private fun java.io.InputStream.readBoundedText(): String {
        val reader = bufferedReader(Charsets.UTF_8)
        val builder = StringBuilder()
        val buffer = CharArray(BUFFER_SIZE)
        while (true) {
            val read = reader.read(buffer)
            if (read == -1) break
            builder.append(buffer, 0, read)
            if (builder.length > MAX_CHARS) throw RecipeImportException(ImportFailure.TooLarge)
        }
        return builder.toString()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val BUFFER_SIZE = 8_192
        const val MAX_CHARS = 3_000_000
        val HTTP_OK_RANGE = 200..299
        const val USER_AGENT =
            "Mozilla/5.0 (Android) Bobitos/1.0 (+recipe-import)"
    }
}
