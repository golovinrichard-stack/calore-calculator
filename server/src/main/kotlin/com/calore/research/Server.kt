package com.calore.research

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.time.Duration.Companion.hours

/**
 * The research server (M10.6). Endpoints:
 * - `POST /v1/participants`: register (the body has the secret; only its SHA-256 hash is stored)
 * - `PUT /v1/participants/{id}`: update categories, profile and consent version
 * - `POST /v1/reports`: one weekly report, idempotent by `reportId`
 * - `DELETE /v1/participants/{id}`: the participant and every report
 * - `GET /v1/health`
 *
 * No IP addresses are stored or logged; rate limits keep them in memory only. Only the ISO week
 * a report arrived in is stored, never a timestamp.
 */
fun Application.researchModule(store: Store, clock: Clock = Clock.systemUTC(), limits: Limits = Limits()) {
    install(RateLimit) {
        register(PER_IP) {
            rateLimiter(limit = limits.perIpPerHour, refillPeriod = 1.hours)
            requestKey { call -> call.clientKey() }
        }
        register(PER_PARTICIPANT) {
            rateLimiter(limit = limits.perParticipantPerHour, refillPeriod = 1.hours)
            requestKey { call -> call.bearer()?.let(Store::hash) ?: call.clientKey() }
        }
    }
    fun week(): String = LocalDate.now(clock).let { "%d-W%02d".format(it.get(IsoFields.WEEK_BASED_YEAR), it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)) }

    routing {
        get("/v1/health") { call.json(HttpStatusCode.OK, """{"status":"ok"}""") }
        rateLimit(PER_IP) {
            post("/v1/participants") {
                val json = call.boundedJson() ?: return@post
                val reg = try {
                    Validation.registration(json)
                } catch (e: Validation.Invalid) {
                    return@post call.error(HttpStatusCode.BadRequest, e.message)
                }
                val participant = Participant(reg.participantId, Store.hash(reg.secret), reg.consentVersion, reg.categories, reg.profile.toString())
                if (store.register(participant, week())) call.json(HttpStatusCode.Created, """{"status":"registered"}""") else call.error(HttpStatusCode.Conflict, "exists")
            }
            rateLimit(PER_PARTICIPANT) {
                put("/v1/participants/{id}") {
                    val participant = call.authorised(store, call.parameters["id"]) ?: return@put
                    val json = call.boundedJson() ?: return@put
                    val update = try {
                        Validation.update(json)
                    } catch (e: Validation.Invalid) {
                        return@put call.error(HttpStatusCode.BadRequest, e.message)
                    }
                    store.update(participant.id, update.consentVersion, update.categories, update.profile.toString())
                    call.json(HttpStatusCode.OK, """{"status":"updated"}""")
                }
                post("/v1/reports") {
                    val json = call.boundedJson() ?: return@post
                    val id = runCatching { json.jsonObject["participantId"]?.jsonPrimitive?.content?.lowercase() }.getOrNull()
                    val participant = call.authorised(store, id) ?: return@post
                    val report = try {
                        Validation.report(json, participant.id, participant.categories)
                    } catch (e: Validation.Invalid) {
                        return@post call.error(HttpStatusCode.BadRequest, e.message)
                    }
                    val stored = store.insertReport(StoredReport(report.reportId, participant.id, report.isoWeek, week(), report.json.toString()))
                    call.json(if (stored) HttpStatusCode.Created else HttpStatusCode.OK, """{"status":"stored"}""")
                }
                delete("/v1/participants/{id}") {
                    val participant = call.authorised(store, call.parameters["id"]) ?: return@delete
                    store.deleteParticipant(participant.id)
                    call.json(HttpStatusCode.OK, """{"status":"deleted"}""")
                }
            }
        }
    }
}

/** Requests per hour (*tunable*); a phone sends a few per week. */
data class Limits(val perIpPerHour: Int = 60, val perParticipantPerHour: Int = 30)

private val PER_IP = RateLimitName("per-ip")
private val PER_PARTICIPANT = RateLimitName("per-participant")

/** For rate limits only, in memory: the client address (behind the proxy, the first forwarded one). */
private fun ApplicationCall.clientKey(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: request.local.remoteAddress

private fun ApplicationCall.bearer(): String? =
    request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()

/** The participant whose secret is in the Authorization header, or an answer (404 / 401) and null. */
private suspend fun ApplicationCall.authorised(store: Store, id: String?): Participant? {
    val secret = bearer() ?: run {
        error(HttpStatusCode.Unauthorized, "secret")
        return null
    }
    val participant = id?.let { store.participant(it.lowercase()) } ?: run {
        error(HttpStatusCode.NotFound, "participant")
        return null
    }
    if (!MessageDigest.isEqual(Store.hash(secret).toByteArray(), participant.secretHash.toByteArray())) {
        error(HttpStatusCode.Unauthorized, "secret")
        return null
    }
    return participant
}

/** The body as JSON, at most 16 KB; otherwise an answer (413 / 400) and null. */
private suspend fun ApplicationCall.boundedJson(): JsonElement? {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > Validation.MAX_BODY_BYTES) {
        error(HttpStatusCode.PayloadTooLarge, "body")
        return null
    }
    val bytes = receiveChannel().readRemaining(Validation.MAX_BODY_BYTES + 1L).readByteArray()
    if (bytes.size > Validation.MAX_BODY_BYTES) {
        error(HttpStatusCode.PayloadTooLarge, "body")
        return null
    }
    return runCatching { Json.parseToJsonElement(bytes.decodeToString()) }.getOrNull() ?: run {
        error(HttpStatusCode.BadRequest, "json")
        null
    }
}

private suspend fun ApplicationCall.json(status: HttpStatusCode, body: String) = respondText(body, ContentType.Application.Json, status)

private suspend fun ApplicationCall.error(status: HttpStatusCode, field: String?) =
    json(status, """{"error":"${(field ?: "invalid").replace("\"", "")}"}""")
