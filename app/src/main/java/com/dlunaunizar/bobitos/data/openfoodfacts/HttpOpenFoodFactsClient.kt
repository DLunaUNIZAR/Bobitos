package com.dlunaunizar.bobitos.data.openfoodfacts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Descarga JSON de una URL https. Aislado tras una interfaz para testear el cliente con fixtures. */
interface OffHttpClient {
    /** @return el cuerpo, o null si el recurso no existe (404). @throws OffException en fallos de red. */
    suspend fun get(url: String): String?
}

class HttpUrlOffHttpClient @Inject constructor() : OffHttpClient {
    override suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                in HTTP_OK_RANGE -> connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                else -> throw OffException(if (code >= HTTP_SERVER_ERROR) OffFailure.Network else OffFailure.Unknown)
            }
        } catch (error: IOException) {
            throw OffException(OffFailure.Network, error)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        val HTTP_OK_RANGE = 200..299
        const val HTTP_SERVER_ERROR = 500
        const val USER_AGENT = "Bobitos/1.0 (Android; recipe app; contacto: bobitos-app)"
    }
}

class HttpOpenFoodFactsClient @Inject constructor(private val http: OffHttpClient) : OpenFoodFactsClient {
    override suspend fun lookup(barcode: String): OffProduct? {
        val code = barcode.trim().filter(Char::isDigit)
        if (code.isEmpty()) return null
        val body = http.get("$BASE_URL/api/v2/product/$code.json?fields=$FIELDS") ?: return null
        return parseOffProduct(body)
    }

    private companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val FIELDS = "product_name,brands,nutriments"
    }
}
