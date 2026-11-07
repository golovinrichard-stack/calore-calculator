package com.calore.tracker.domain.research

import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.Supplement
import com.calore.tracker.domain.SupplementKind
import com.calore.tracker.domain.SupplementLog
import com.calore.tracker.domain.SupplementSchedule
import com.calore.tracker.domain.SupplementStatus
import com.calore.tracker.domain.WeightEntry
import com.calore.tracker.domain.cycle.Contraception
import com.calore.tracker.domain.cycle.CycleDay
import com.calore.tracker.domain.cycle.CyclePeriod
import com.calore.tracker.domain.cycle.Symptom
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ResearchReportsTest {

    private val monday = LocalDate.of(2026, 11, 2)
    private val week = monday..monday.plusDays(6)
    private val zone = ZoneOffset.UTC

    private fun day(offset: Long, kcal: Double, type: EntryType = EntryType.FOOD, meal: MealType = MealType.LUNCH, sections: Map<ShopSection, Double> = mapOf(ShopSection.MEAT to 1.0)) =
        ResearchEntry(monday.plusDays(offset), meal, kcal, protein = kcal / 20, carbs = kcal / 8, fat = kcal / 30, fiber = 5.0, sugar = 10.0, type = type, sections = sections)

    private fun fullReport(): JsonObject {
        val entries = (0L..4L).flatMap { listOf(day(it, 1200.0), day(it, 800.0, EntryType.BOX, MealType.DINNER, mapOf(ShopSection.GRAINS to 0.5, ShopSection.VEGETABLES to 0.5))) }
        val periods = listOf(CyclePeriod("a", monday.minusDays(25), monday.minusDays(21)), CyclePeriod("b", monday.plusDays(3)))
        val supplement = Supplement(id = "s", name = "Vitamin D 2000", kind = SupplementKind.VITAMIN_D, startDate = monday)
        val doses = (0L..6L).map { SupplementSchedule.doses(supplement, monday.plusDays(it), zone).single() }
        val sections = mapOf(
            ResearchCategory.NUTRITION to ResearchReports.nutrition(week, entries, 2200, plannedSlots = 10, doneSlots = 8, boxesWasted = 1),
            ResearchCategory.BODY to ResearchReports.body(
                week,
                170.0,
                listOf(WeightEntry(1, monday.minusDays(3), 70.0, "w1"), WeightEntry(2, monday.plusDays(5), 69.5, "w2")),
                listOf(monday.minusDays(10) to 23.4),
            ),
            ResearchCategory.CYCLE to ResearchReports.cycle(
                week, periods,
                listOf(CycleDay("d", monday.minusDays(2), symptoms = setOf(Symptom.CRAVINGS), note = "private note 2026-11-01")),
                Contraception.PILL, 5, mapOf(monday.minusDays(2) to 2213.0), 2200,
            ),
            ResearchCategory.SUPPLEMENTS to ResearchReports.supplements(
                week, listOf(supplement), doses.take(6).map { SupplementLog("l${it.date}", "s", it.scheduledAt, it.scheduledAt, SupplementStatus.TAKEN) }, zone,
            ),
        )
        return ResearchReports.report("r1", "p1", monday, "0.11.0", sections, ResearchCategory.entries.toSet())
    }

    @Test
    fun aReportNeverContainsAFieldOutsideAppendixA() {
        val report = fullReport()
        val paths = ResearchReports.normalisedPaths(report)
        assertEquals(emptySet<String>(), paths - ResearchReports.ALLOWED_REPORT_FIELDS)
        // All four sections are there in this example.
        assertTrue(listOf("nutrition", "body", "cycle", "supplements").all { it in report })
    }

    @Test
    fun noDatesFinerThanAnIsoWeekAndNoFreeText() {
        val text = fullReport().toString()
        assertFalse(text, Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(text))
        assertFalse("notes and supplement names never leave the phone", text.contains("private note") || text.contains("Vitamin D 2000"))
        assertEquals("2026-W45", fullReport()["isoWeek"]!!.jsonPrimitive.content)
    }

    @Test
    fun bandsAndRounding() {
        assertNull(ResearchReports.ageBand(17))
        assertEquals("18-24", ResearchReports.ageBand(18))
        assertEquals("18-24", ResearchReports.ageBand(24))
        assertEquals("25-29", ResearchReports.ageBand(25))
        assertEquals("60-64", ResearchReports.ageBand(64))
        assertEquals("65+", ResearchReports.ageBand(65))
        assertEquals("<18.5", ResearchReports.bmiBand(18.4))
        assertEquals("18.5-24.9", ResearchReports.bmiBand(18.5))
        assertEquals("25-29.9", ResearchReports.bmiBand(29.99))
        assertEquals("30-34.9", ResearchReports.bmiBand(30.0))
        assertEquals(">=35", ResearchReports.bmiBand(35.0))
        assertEquals("20-24", ResearchReports.bodyFatBand(23.4))
        assertEquals("25-29", ResearchReports.bodyFatBand(25.0))
        assertEquals(2140, ResearchReports.roundKcal(2137.0))
        assertEquals(2150, ResearchReports.roundKcal(2145.0))
        assertEquals(-0.4, ResearchReports.roundKg(-0.44), 0.0)
        assertEquals("2026-W01", ResearchReports.isoWeek(LocalDate.of(2025, 12, 29)))

        val report = fullReport()
        val nutrition = report["nutrition"]!!.jsonObject
        assertEquals(5, nutrition["countedDays"]!!.jsonPrimitive.int)
        assertEquals(2000, nutrition["avgKcal"]!!.jsonPrimitive.int)
        assertEquals(100, nutrition["avgProteinG"]!!.jsonPrimitive.int)
        assertEquals("2.0", nutrition["avgMealsPerDay"]!!.jsonPrimitive.content)
        assertEquals(40, nutrition["prepBoxSharePct"]!!.jsonPrimitive.int)
        assertEquals(80, nutrition["planAdherencePct"]!!.jsonPrimitive.int)
        assertEquals(mapOf("MEAT" to 60, "VEGETABLES" to 20, "GRAINS" to 20), nutrition["kcalShareBySection"]!!.jsonObject.mapValues { it.value.jsonPrimitive.int })
        val body = report["body"]!!.jsonObject
        assertEquals("18.5-24.9", body["bmiBand"]!!.jsonPrimitive.content)
        assertEquals("20-24", body["bodyFatBand"]!!.jsonPrimitive.content)
        val cycle = report["cycle"]!!.jsonObject["cycles"]!!.jsonArray.single().jsonObject
        assertEquals(28, cycle["cycleLengthDays"]!!.jsonPrimitive.int)
        assertEquals(5, cycle["periodLengthDays"]!!.jsonPrimitive.int)
        assertEquals("HORMONAL", cycle["contraception"]!!.jsonPrimitive.content)
        assertEquals(1, cycle["symptomDaysByPhase"]!!.jsonObject["LUTEAL"]!!.jsonObject["CRAVINGS"]!!.jsonPrimitive.int)
        assertEquals(2210, cycle["avgKcalByPhase"]!!.jsonObject["LUTEAL"]!!.jsonPrimitive.int)
        val kinds = report["supplements"]!!.jsonObject["kinds"]!!.jsonArray.single().jsonObject
        assertEquals("VITAMIN_D", kinds["kind"]!!.jsonPrimitive.content)
        assertEquals(86, kinds["adherencePct"]!!.jsonPrimitive.int)
    }

    @Test
    fun nutritionIsSkippedBelowThreeCountedDays() {
        // Two full days and one day with less than half the target.
        val entries = listOf(day(0, 2000.0), day(1, 2000.0), day(2, 900.0))
        assertNull(ResearchReports.nutrition(week, entries, 2200, 0, 0, 0))
        val three = ResearchReports.nutrition(week, entries + day(3, 1500.0), 2200, 0, 0, 0)!!
        assertEquals(3, three["countedDays"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, three["planAdherencePct"])
        // Entries outside the week don't count.
        assertNull(ResearchReports.nutrition(week, listOf(day(-1, 2000.0), day(7, 2000.0), day(8, 2000.0)), 2200, 0, 0, 0))
    }

    @Test
    fun categoriesThatWerentAgreedToAreAbsent() {
        val full = fullReport()
        val sections = ResearchCategory.entries.associateWith { full[it.name.lowercase()] as JsonObject? }
        val onlyNutrition = ResearchReports.report("r1", "p1", monday, "0.11.0", sections, setOf(ResearchCategory.NUTRITION))
        assertEquals(setOf("reportId", "participantId", "reportVersion", "isoWeek", "appVersion", "nutrition"), onlyNutrition.keys)
        // A section with nothing to say is left out even when agreed to.
        val none = ResearchReports.report("r1", "p1", monday, "0.11.0", mapOf(ResearchCategory.CYCLE to null), setOf(ResearchCategory.CYCLE))
        assertFalse("cycle" in none)
    }

    @Test
    fun theRegistrationHasOnlyAppendixAFields() {
        val profile = ResearchProfile(LocalDate.of(1998, 5, 1), Sex.FEMALE, "CZ-64", Diet.OMNIVORE, GoalType.CUT, ActivityLevel.MODERATE)
        val json = ResearchReports.registration("p1", setOf(ResearchCategory.CYCLE, ResearchCategory.NUTRITION), profile, LocalDate.of(2026, 11, 7))
        assertEquals(emptySet<String>(), ResearchReports.fieldPaths(json) - ResearchReports.ALLOWED_REGISTRATION_FIELDS)
        assertEquals("25-29", json["profile"]!!.jsonObject["ageBand"]!!.jsonPrimitive.content)
        assertEquals(listOf("NUTRITION", "CYCLE"), json["categories"]!!.jsonArray.map { it.jsonPrimitive.content })
        // An unknown region is never sent.
        val odd = ResearchReports.registration("p1", setOf(ResearchCategory.BODY), profile.copy(region = "Brno, Kounicova 12"), LocalDate.of(2026, 11, 7))
        assertEquals(JsonNull, odd["profile"]!!.jsonObject["region"])
    }
}
