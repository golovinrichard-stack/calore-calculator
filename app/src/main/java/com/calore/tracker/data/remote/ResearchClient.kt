package com.calore.tracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The research server (M10.4, M10.6), with `HttpURLConnection` as the rest of the app. Every call
 * sends only the JSON built from policy appendix A; nothing else (no cookies, no device ids).
 * [baseUrl] comes from `BuildConfig.RESEARCH_SERVER_URL`; when it is empty the feature is hidden.
 */
open class ResearchClient(private val baseUrl: String, private val userAgent: String) {

    /** The result of one call: success, a refusal by the server, or no network. */
    sealed interface Result {
        data object Ok : Result
        data class Refused(val code: Int) : Result
        data class Failed(val error: IOException) : Result
    }

    open suspend fun register(body: String): Result = call("POST", "/v1/participants", body, secret = null)

    open suspend fun update(participantId: String, secret: String, body: String): Result = call("PUT", "/v1/participants/$participantId", body, secret)

    open suspend fun sendReport(secret: String, body: String): Result = call("POST", "/v1/reports", body, secret)

    open suspend fun deleteParticipant(participantId: String, secret: String): Result = call("DELETE", "/v1/participants/$participantId", null, secret)

    private suspend fun call(method: String, path: String, body: String?, secret: String?): Result = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext Result.Failed(IOException("No research server configured"))
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            secret?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                // The JSON as stored, also for the registration (it carries the secret, sent once over HTTPS).
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            if (code in 200..299) Result.Ok else Result.Refused(code)
        } catch (e: IOException) {
            Result.Failed(e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
