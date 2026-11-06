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

    /**
     * Searches by name. With a [country] (an ISO code such as "CZ"), products sold there come
     * first; when there are only a few, the rest of the world fills the list (M2.3). Only the
     * query, the languages and the country are sent.
     */
    suspend fun search(
        query: String,
        locale: Locale = Locale.getDefault(),
        country: String? = null,
    ): RemoteResult<List<RemoteFood>> = withContext(ioDispatcher) {
        val tag = country?.let { COUNTRY_TAGS[it.uppercase()] }
        val words = searchWords(query)
        if (tag == null || words.isEmpty()) return@withContext searchOnce(query, locale)

        val local = searchOnce("$words countries_tags:\"$tag\"", locale)
        if (local is RemoteResult.Success && local.value.size >= MIN_LOCAL_RESULTS) return@withContext local
        if (local is RemoteResult.NetworkError) return@withContext local
        when (val everywhere = searchOnce(query, locale)) {
            is RemoteResult.Success -> {
                val first = (local as? RemoteResult.Success)?.value.orEmpty()
                RemoteResult.Success((first + everywhere.value).distinctBy { it.barcode }.take(PAGE_SIZE))
            }
            else -> if (local is RemoteResult.Success) local else everywhere
        }
    }

    /** One product by barcode (M2.4). Only the barcode is sent, with the fields Calore reads. */
    suspend fun product(barcode: String): RemoteResult<OpenFoodFactsParser.ProductLookup> = withContext(ioDispatcher) {
        val url = URL("$PRODUCT_ENDPOINT/${URLEncoder.encode(barcode, "UTF-8")}?fields=${URLEncoder.encode(FIELDS, "UTF-8")}")
        val body = try {
            fetch(url, acceptNotFound = true)
        } catch (e: IOException) {
            return@withContext RemoteResult.NetworkError
        } ?: return@withContext RemoteResult.BadResponse
        when (val lookup = OpenFoodFactsParser.parseProduct(body, barcode)) {
            OpenFoodFactsParser.ProductLookup.Unreadable -> RemoteResult.BadResponse
            else -> RemoteResult.Success(lookup)
        }
    }

    private fun searchOnce(query: String, locale: Locale): RemoteResult<List<RemoteFood>> {
        val body = try {
            fetch(searchUrl(query, locale))
        } catch (e: IOException) {
            return RemoteResult.NetworkError
        } ?: return RemoteResult.BadResponse
        return OpenFoodFactsParser.parseSearch(body)?.let { RemoteResult.Success(it) } ?: RemoteResult.BadResponse
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

    /**
     * Returns the body of a 200 response (and of a 404 when [acceptNotFound], which the product
     * endpoint uses for unknown barcodes), or null for any other status or an oversized body.
     */
    private fun fetch(url: URL, acceptNotFound: Boolean = false): String? {
        val connection = url.openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = when {
                code == HttpURLConnection.HTTP_OK -> connection.inputStream
                code == HttpURLConnection.HTTP_NOT_FOUND && acceptNotFound -> connection.errorStream ?: return """{"status":0}"""
                else -> return null
            }
            stream.use { it.readCapped(MAX_BODY_BYTES) }?.toString(Charsets.UTF_8)
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

    internal companion object {
        const val SEARCH_ENDPOINT = "https://search.openfoodfacts.org/search"
        const val PRODUCT_ENDPOINT = "https://world.openfoodfacts.org/api/v2/product"
        const val FIELDS =
            "code,product_name,brands,nutriments,serving_quantity,serving_quantity_unit,serving_size," +
                "quantity,product_quantity,product_quantity_unit,stores_tags,categories_tags,allergens_tags"

        /** Fewer local results than this and the search also looks worldwide. */
        const val MIN_LOCAL_RESULTS = 10

        /** Open Food Facts country tags for the countries Calore supports. */
        val COUNTRY_TAGS = mapOf("CZ" to "en:czech-republic")

        /** The user's words without characters that have a meaning in the search syntax. */
        fun searchWords(query: String): String =
            query.replace(Regex("""[:"()\[\]{}^~*?\\/!+\-&|<>=]"""), " ").trim().replace(Regex("""\s+"""), " ")
        const val PAGE_SIZE = 25
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 4 * 1024 * 1024
        const val BUFFER_BYTES = 16 * 1024
    }
}
