package com.calore.tracker.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>

    /** No connection, timeout, or the server could not be reached. */
    data object NetworkError : RemoteResult<Nothing>

    /** The server answered, but not with something we could read. */
    data object BadResponse : RemoteResult<Nothing>
}

/**
 * Minimal Open Food Facts client built on [HttpURLConnection], so the app needs no
 * networking library. Only text search is used; nothing about the user is sent
 * except the query itself.
 */
class OpenFoodFactsClient(
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun search(query: String, locale: Locale = Locale.getDefault()): RemoteResult<List<RemoteFood>> =
        withContext(ioDispatcher) {
            val body = try {
                fetch(searchUrl(query, locale))
            } catch (e: IOException) {
                return@withContext RemoteResult.NetworkError
            } ?: return@withContext RemoteResult.BadResponse

            OpenFoodFactsParser.parseSearch(body)
                ?.let { RemoteResult.Success(it) }
                ?: RemoteResult.BadResponse
        }

    private fun searchUrl(query: String, locale: Locale): URL {
        val language = locale.language.takeIf { it.isNotBlank() && it != "en" }
        val langs = listOfNotNull(language, "en").joinToString(",")
        val params = listOf(
            "q" to query,
            "langs" to langs,
            "page_size" to PAGE_SIZE.toString(),
            "fields" to FIELDS,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return URL("$SEARCH_ENDPOINT?$params")
    }

    /** Returns the body of a 200 response, or null for any other status or an oversized body. */
    private fun fetch(url: URL): String? {
        val connection = url.openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readCapped(MAX_BODY_BYTES) }?.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readCapped(maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val SEARCH_ENDPOINT = "https://search.openfoodfacts.org/search"
        const val FIELDS =
            "code,product_name,brands,nutriments,serving_quantity,serving_quantity_unit,serving_size"
        const val PAGE_SIZE = 25
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 4 * 1024 * 1024
        const val BUFFER_BYTES = 16 * 1024
    }
}
