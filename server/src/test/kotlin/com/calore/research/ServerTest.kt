package com.calore.research

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-09T06:00:00Z"), ZoneOffset.UTC)
    private val store = Store("jdbc:h2:mem:t${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1").also { it.migrate() }
    private val id = UUID.randomUUID().toString()
    private val secret = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    private val profile = """{"ageBand":"25-29","sex":"FEMALE","region":"CZ-64","diet":"OMNIVORE","goalType":"CUT","activity":"MODERATE"}"""
    private fun registration(categories: String = """["NUTRITION","CYCLE"]""") =
        """{"participantId":"$id","secret":"$secret","consentVersion":1,"categories":$categories,"profile":$profile}"""

    /** The example of policy appendix A, for this participant. */
    private fun report(reportId: String = UUID.randomUUID().toString(), extra: String = "") = """
        {"reportId":"$reportId","participantId":"$id","reportVersion":1,"isoWeek":"2026-W45","appVersion":"0.12.0",
         "nutrition":{"countedDays":6,"avgKcal":2140,"avgProteinG":128,"avgCarbsG":231,"avgFatG":74,"avgFiberG":24,"avgSugarG":52,
           "avgMealsPerDay":3.6,"targetKcal":2200,"kcalShareBySection":{"MEAT":18,"GRAINS":24,"DAIRY":15,"VEGETABLES":6,"FRUIT":5,"OTHER":32},
           "prepBoxSharePct":55,"quickAddSharePct":5,"planAdherencePct":80,"boxesWasted":1}$extra}
    """.trimIndent()

    private val cycle = ""","cycle":{"cycles":[{"cycleLengthDays":29,"periodLengthDays":5,"contraception":"NONE",
        "symptomDaysByPhase":{"LUTEAL":{"CRAVINGS":3}},"avgKcalByPhase":{"PERIOD":2050,"LUTEAL":2210}}]}"""

    @Test
    fun registerSendUpdateAndDelete() = testApplication {
        application { researchModule(store, clock) }
        assertEquals(HttpStatusCode.OK, client.get("/v1/health").status)

        val registered = client.post("/v1/participants") {
            contentType(ContentType.Application.Json)
            setBody(registration())
        }
        assertEquals(HttpStatusCode.Created, registered.status)
        // The secret itself is never stored, only its hash.
        val stored = store.participant(id)!!
        assertEquals(Store.hash(secret), stored.secretHash)
        assertFalse(stored.profile.contains(secret))
        assertEquals(HttpStatusCode.Conflict, client.post("/v1/participants") { setBody(registration()) }.status)

        val reportId = UUID.randomUUID().toString()
        val sent = client.post("/v1/reports") {
            header(HttpHeaders.Authorization, "Bearer $secret")
            setBody(report(reportId, cycle))
        }
        assertEquals(HttpStatusCode.Created, sent.status)
        // Idempotent by reportId.
        assertEquals(HttpStatusCode.OK, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(report(reportId, cycle)) }.status)
        assertEquals(1, store.reportsWithProfiles().size)
        assertEquals("2026-W46", store.reportsWithProfiles().single().first.receivedWeek)

        val updated = client.put("/v1/participants/$id") {
            header(HttpHeaders.Authorization, "Bearer $secret")
            setBody("""{"consentVersion":1,"categories":["NUTRITION"],"profile":$profile}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals(setOf("NUTRITION"), store.participant(id)!!.categories)

        val deleted = client.delete("/v1/participants/$id") { header(HttpHeaders.Authorization, "Bearer $secret") }
        assertEquals(HttpStatusCode.OK, deleted.status)
        // M10.5 "done when": no rows left for that participant.
        assertEquals(0, store.countRows(id))
        assertEquals(HttpStatusCode.NotFound, client.delete("/v1/participants/$id") { header(HttpHeaders.Authorization, "Bearer $secret") }.status)
    }

    @Test
    fun refusesWrongSecretsUnknownFieldsUnagreedCategoriesAndBigBodies() = testApplication {
        application { researchModule(store, clock) }
        client.post("/v1/participants") { setBody(registration("""["NUTRITION"]""")) }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/reports") { setBody(report()) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer wrong"); setBody(report()) }.status)
        // A section the participant didn't agree to.
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(report(extra = cycle)) }.status)
        // A field outside appendix A.
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(report(extra = ""","name":"Anna"""")) }.status)
        // A value out of range, and a date instead of a week.
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(report().replace("\"countedDays\":6", "\"countedDays\":2")) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(report().replace("2026-W45", "2026-11-04")) }.status)
        // Over 16 KB.
        val big = report().dropLast(1) + ",\"x\":\"" + "a".repeat(17_000) + "\"}"
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(big) }.status)
        assertEquals(0, store.reportsWithProfiles().size)
        // A registration with an unknown field or a short secret.
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/participants") { setBody(registration().replace("\"consentVersion\"", "\"email\":\"a@b.c\",\"consentVersion\"")) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/participants") { setBody(registration().replace(secret, "c2hvcnQ=")) }.status)
    }

    @Test
    fun rateLimitsPerParticipant() = testApplication {
        application { researchModule(store, clock, Limits(perIpPerHour = 100, perParticipantPerHour = 3)) }
        client.post("/v1/participants") { setBody(registration()) }
        val codes = (1..5).map { client.post("/v1/reports") { header(HttpHeaders.Authorization, "Bearer $secret"); setBody(report()) }.status }
        assertEquals(3, codes.count { it == HttpStatusCode.Created })
        assertTrue(HttpStatusCode.TooManyRequests in codes)
    }
}
