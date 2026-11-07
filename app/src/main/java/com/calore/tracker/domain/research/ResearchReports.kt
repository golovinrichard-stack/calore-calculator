package com.calore.tracker.domain.research

import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.EntryType
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.ShopSection
import com.calore.tracker.domain.SmartMaintenance
import com.calore.tracker.domain.Supplement
import com.calore.tracker.domain.SupplementKind
import com.calore.tracker.domain.SupplementLog
import com.calore.tracker.domain.SupplementSchedule
import com.calore.tracker.domain.SupplementStatus
import com.calore.tracker.domain.TrendWeight
import com.calore.tracker.domain.WeightEntry
import com.calore.tracker.domain.cycle.Contraception
import com.calore.tracker.domain.cycle.CycleDay
import com.calore.tracker.domain.cycle.CyclePeriod
import com.calore.tracker.domain.cycle.Cycles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.roundToInt

/** The kinds of data someone can agree to send (M10.2). */
enum class ResearchCategory { NUTRITION, BODY, CYCLE, SUPPLEMENTS }

/** Changing what is collected means a new version and asking again (M10.1). */
const val RESEARCH_CONSENT_VERSION = 1

/** The weekly report's format version (policy appendix A). */
const val RESEARCH_REPORT_VERSION = 1

/** One diary entry, as the report sees it: nutrients, kind, and its calories' share per food group. */
data class ResearchEntry(
    val date: LocalDate,
    val meal: MealType,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val type: EntryType = EntryType.FOOD,
    /** Fractions adding up to 1; empty means OTHER. */
    val sections: Map<ShopSection, Double> = emptyMap(),
)

/** What a registration says about the person (policy appendix A), all in bands or categories. */
data class ResearchProfile(
    val birthDate: LocalDate?,
    val sex: Sex?,
    /** ISO 3166-2:CZ, e.g. `CZ-64`, or null. */
    val region: String?,
    val diet: Diet,
    val goalType: GoalType?,
    val activity: ActivityLevel?,
)

/**
 * Research reports (algorithms A21). The JSON has exactly the fields of policy appendix A;
 * [ALLOWED_REPORT_FIELDS] and [ALLOWED_REGISTRATION_FIELDS] list them, and tests check every report
 * against them. No names, exact dates, food names, notes or free text, ever.
 */
object ResearchReports {

    /** The 14 Czech regions (kraje), ISO 3166-2:CZ. */
    val REGIONS = listOf("CZ-10", "CZ-20", "CZ-31", "CZ-32", "CZ-41", "CZ-42", "CZ-51", "CZ-52", "CZ-53", "CZ-63", "CZ-64", "CZ-71", "CZ-72", "CZ-80")

    /** Nutrition is sent only for weeks with at least this many counted days (A21). */
    const val MIN_COUNTED_DAYS = 3

    /** A body-fat value older than this is not sent. */
    const val BODY_FAT_MAX_AGE_DAYS = 90L

    /** Dotted paths of every field a weekly report may have; `[]` marks list items and `{}` map values. */
    val ALLOWED_REPORT_FIELDS: Set<String> = setOf(
        "reportId", "participantId", "reportVersion", "isoWeek", "appVersion",
        "nutrition", "nutrition.countedDays", "nutrition.avgKcal", "nutrition.avgProteinG", "nutrition.avgCarbsG", "nutrition.avgFatG",
        "nutrition.avgFiberG", "nutrition.avgSugarG", "nutrition.avgMealsPerDay", "nutrition.targetKcal", "nutrition.kcalShareBySection",
        "nutrition.kcalShareBySection.{}", "nutrition.prepBoxSharePct", "nutrition.quickAddSharePct", "nutrition.planAdherencePct",
        "nutrition.boxesWasted",
        "body", "body.bmiBand", "body.trendChangeKg", "body.bodyFatBand",
        "cycle", "cycle.cycles", "cycle.cycles[]", "cycle.cycles[].cycleLengthDays", "cycle.cycles[].periodLengthDays",
        "cycle.cycles[].contraception", "cycle.cycles[].symptomDaysByPhase", "cycle.cycles[].symptomDaysByPhase.{}",
        "cycle.cycles[].symptomDaysByPhase.{}.{}", "cycle.cycles[].avgKcalByPhase", "cycle.cycles[].avgKcalByPhase.{}",
        "supplements", "supplements.kinds", "supplements.kinds[]", "supplements.kinds[].kind", "supplements.kinds[].adherencePct",
    )

    val ALLOWED_REGISTRATION_FIELDS: Set<String> = setOf(
        "participantId", "consentVersion", "categories", "categories[]",
        "profile", "profile.ageBand", "profile.sex", "profile.region", "profile.diet", "profile.goalType", "profile.activity",
    )

    // --- Bands and rounding (A21) ---

    fun isoWeek(date: LocalDate): String = "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** 18–24, 25–29 … 60–64, 65+; null under 18 (research is for adults only). */
    fun ageBand(age: Int): String? = when {
        age < 18 -> null
        age <= 24 -> "18-24"
        age >= 65 -> "65+"
        else -> (age / 5 * 5).let { "$it-${it + 4}" }
    }

    fun bmiBand(bmi: Double): String = when {
        bmi < 18.5 -> "<18.5"
        bmi < 25.0 -> "18.5-24.9"
        bmi < 30.0 -> "25-29.9"
        bmi < 35.0 -> "30-34.9"
        else -> ">=35"
    }

    /** 5 % bands: 23.4 → "20-24". */
    fun bodyFatBand(pct: Double): String = (floor(pct / 5).toInt() * 5).let { "$it-${it + 4}" }

    fun roundKcal(kcal: Double): Int = (kcal / 10).roundToInt() * 10

    fun roundGrams(grams: Double): Int = grams.roundToInt()

    fun roundPct(pct: Double): Int = pct.roundToInt()

    fun roundKg(kg: Double): Double = Math.round(kg * 10) / 10.0

    // --- Sections ---

    /** Nutrition (A21): averages per counted day; null below [MIN_COUNTED_DAYS]. */
    fun nutrition(
        week: ClosedRange<LocalDate>,
        entries: List<ResearchEntry>,
        dailyTargetKcal: Int,
        plannedSlots: Int,
        doneSlots: Int,
        boxesWasted: Int,
    ): JsonObject? {
        val inWeek = entries.filter { it.date in week }
        val byDay = inWeek.groupBy { it.date }
        val threshold = dailyTargetKcal * SmartMaintenance.COUNTED_SHARE
        val counted = byDay.filterValues { day -> day.sumOf { it.kcal } >= threshold }
        if (counted.size < MIN_COUNTED_DAYS) return null
        val days = counted.size.toDouble()
        val all = counted.values.flatten()
        val totalKcal = all.sumOf { it.kcal }
        fun avg(f: (ResearchEntry) -> Double?) = all.sumOf { f(it) ?: 0.0 } / days
        val shares = HashMap<ShopSection, Double>()
        for (e in all) {
            val split = e.sections.ifEmpty { mapOf(ShopSection.OTHER to 1.0) }
            split.forEach { (section, fraction) -> shares[section] = (shares[section] ?: 0.0) + e.kcal * fraction }
        }
        return buildJsonObject {
            put("countedDays", counted.size)
            put("avgKcal", roundKcal(avg { it.kcal }))
            put("avgProteinG", roundGrams(avg { it.protein }))
            put("avgCarbsG", roundGrams(avg { it.carbs }))
            put("avgFatG", roundGrams(avg { it.fat }))
            put("avgFiberG", roundGrams(avg { it.fiber }))
            put("avgSugarG", roundGrams(avg { it.sugar }))
            put("avgMealsPerDay", Math.round(counted.values.sumOf { day -> day.map { it.meal }.distinct().size }.toDouble() / days * 10) / 10.0)
            put("targetKcal", dailyTargetKcal)
            putJsonObject("kcalShareBySection") {
                if (totalKcal > 0) {
                    shares.entries.sortedBy { it.key.ordinal }.forEach { (section, kcal) ->
                        val pct = roundPct(kcal / totalKcal * 100)
                        if (pct > 0) put(section.name, pct)
                    }
                }
            }
            put("prepBoxSharePct", if (totalKcal > 0) roundPct(all.filter { it.type == EntryType.BOX }.sumOf { it.kcal } / totalKcal * 100) else 0)
            put("quickAddSharePct", if (totalKcal > 0) roundPct(all.filter { it.type == EntryType.QUICK_ADD }.sumOf { it.kcal } / totalKcal * 100) else 0)
            put("planAdherencePct", if (plannedSlots > 0) JsonPrimitive(roundPct(doneSlots * 100.0 / plannedSlots)) else JsonNull)
            put("boxesWasted", boxesWasted)
        }
    }

    /** Body (A21): BMI band, the week's trend change and a recent body-fat band; null when nothing is known. */
    fun body(week: ClosedRange<LocalDate>, heightCm: Double?, weighIns: List<WeightEntry>, bodyFat: List<Pair<LocalDate, Double>>): JsonObject? {
        val upToEnd = weighIns.filter { !it.date.isAfter(week.endInclusive) }
        val latest = upToEnd.maxByOrNull { it.date }
        val bmi = if (heightCm != null && heightCm > 0 && latest != null) latest.kg / ((heightCm / 100) * (heightCm / 100)) else null
        val trend = TrendWeight.of(upToEnd)
        val end = trend.lastOrNull { !it.date.isAfter(week.endInclusive) && !it.date.isBefore(week.start) }
        val before = trend.lastOrNull { it.date.isBefore(week.start) }
        val change = if (end != null && before != null) roundKg(end.trendKg - before.trendKg) else null
        val fat = bodyFat.filter { !it.first.isAfter(week.endInclusive) && !it.first.isBefore(week.endInclusive.minusDays(BODY_FAT_MAX_AGE_DAYS)) }
            .maxByOrNull { it.first }?.second
        if (bmi == null && change == null && fat == null) return null
        return buildJsonObject {
            put("bmiBand", bmi?.let { JsonPrimitive(bmiBand(it)) } ?: JsonNull)
            put("trendChangeKg", change?.let { JsonPrimitive(it) } ?: JsonNull)
            put("bodyFatBand", fat?.let { JsonPrimitive(bodyFatBand(it)) } ?: JsonNull)
        }
    }

    /** Cycle (A21): cycles that ended this week, with no dates; null when none ended. */
    fun cycle(
        week: ClosedRange<LocalDate>,
        periods: List<CyclePeriod>,
        days: List<CycleDay>,
        contraception: Contraception,
        defaultPeriodLength: Int,
        dailyKcal: Map<LocalDate, Double>,
        dailyTargetKcal: Int,
    ): JsonObject? {
        val starts = periods.sortedBy { it.start }
        val ended = starts.zipWithNext().filter { (_, next) -> next.start in week }
        if (ended.isEmpty()) return null
        val threshold = dailyTargetKcal * SmartMaintenance.COUNTED_SHARE
        val dayLogs = days.associateBy { it.date }
        return buildJsonObject {
            put(
                "cycles",
                buildJsonArray {
                    for ((period, next) in ended) {
                        val length = ChronoUnit.DAYS.between(period.start, next.start).toInt()
                        val periodLength = period.end?.let { ChronoUnit.DAYS.between(period.start, it).toInt() + 1 }
                        val phases = Cycles.days(period.start, next.start, periodLength ?: defaultPeriodLength)
                        val symptoms = HashMap<String, MutableMap<String, Int>>()
                        val kcal = HashMap<String, MutableList<Double>>()
                        var d = period.start
                        while (d.isBefore(next.start)) {
                            val phase = phases.phaseOn(d)?.name
                            if (phase != null) {
                                dayLogs[d]?.symptoms?.forEach { s ->
                                    val counts = symptoms.getOrPut(phase) { HashMap() }
                                    counts[s.name] = (counts[s.name] ?: 0) + 1
                                }
                                dailyKcal[d]?.takeIf { it >= threshold }?.let { kcal.getOrPut(phase) { ArrayList() }.add(it) }
                            }
                            d = d.plusDays(1)
                        }
                        add(
                            buildJsonObject {
                                put("cycleLengthDays", length)
                                put("periodLengthDays", periodLength?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("contraception", contraceptionGroup(contraception))
                                putJsonObject("symptomDaysByPhase") {
                                    symptoms.toSortedMap().forEach { (phase, counts) ->
                                        putJsonObject(phase) { counts.toSortedMap().forEach { (s, n) -> put(s, n) } }
                                    }
                                }
                                putJsonObject("avgKcalByPhase") {
                                    kcal.toSortedMap().forEach { (phase, list) -> put(phase, roundKcal(list.average())) }
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    /** Contraception is sent only as NONE, HORMONAL or COPPER_IUD. */
    fun contraceptionGroup(c: Contraception): String = when {
        c.hormonal -> "HORMONAL"
        c == Contraception.COPPER_IUD -> "COPPER_IUD"
        else -> "NONE"
    }

    /** Supplements (A21): kinds only, with how regularly they were taken this week; null without any due. */
    fun supplements(week: ClosedRange<LocalDate>, supplements: List<Supplement>, logs: List<SupplementLog>, zone: ZoneId): JsonObject? {
        val byKind = LinkedHashMap<SupplementKind, IntArray>()
        for (s in supplements.sortedBy { it.kind.ordinal }) {
            val keys = generateSequence(week.start) { it.plusDays(1) }.takeWhile { !it.isAfter(week.endInclusive) }
                .flatMap { SupplementSchedule.doses(s, it, zone) }.map { it.scheduledAt }.toSet()
            if (keys.isEmpty()) continue
            val taken = logs.count { it.supplementId == s.id && it.scheduledAt in keys && it.status == SupplementStatus.TAKEN }
            val counts = byKind.getOrPut(s.kind) { IntArray(2) }
            counts[0] += keys.size
            counts[1] += taken
        }
        if (byKind.isEmpty()) return null
        return buildJsonObject {
            put(
                "kinds",
                buildJsonArray {
                    byKind.forEach { (kind, counts) ->
                        add(
                            buildJsonObject {
                                put("kind", kind.name)
                                put("adherencePct", roundPct(counts[1] * 100.0 / counts[0]))
                            },
                        )
                    }
                },
            )
        }
    }

    /** The weekly report: only the agreed categories that have something to say. */
    fun report(
        reportId: String,
        participantId: String,
        week: LocalDate,
        appVersion: String,
        sections: Map<ResearchCategory, JsonObject?>,
        agreed: Set<ResearchCategory>,
    ): JsonObject = buildJsonObject {
        put("reportId", reportId)
        put("participantId", participantId)
        put("reportVersion", RESEARCH_REPORT_VERSION)
        put("isoWeek", isoWeek(week))
        put("appVersion", appVersion)
        for (category in ResearchCategory.entries) {
            if (category !in agreed) continue
            val section = sections[category] ?: continue
            put(category.name.lowercase(), section)
        }
    }

    /** Registration (appendix A): sent when agreeing, and again only when one of these values changes. */
    fun registration(participantId: String, categories: Set<ResearchCategory>, profile: ResearchProfile, today: LocalDate): JsonObject = buildJsonObject {
        put("participantId", participantId)
        put("consentVersion", RESEARCH_CONSENT_VERSION)
        put("categories", JsonArray(categories.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
        putJsonObject("profile") {
            put("ageBand", profile.birthDate?.let { ageBand(com.calore.tracker.domain.ageOn(it, today)) }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("sex", profile.sex?.name?.let { JsonPrimitive(it) } ?: JsonNull)
            put("region", profile.region?.takeIf { it in REGIONS }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("diet", profile.diet.name)
            put("goalType", profile.goalType?.name?.let { JsonPrimitive(it) } ?: JsonNull)
            put("activity", profile.activity?.name?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    /** Every dotted field path in [json], for checking against the allowed lists. */
    fun fieldPaths(json: JsonElement, prefix: String = ""): Set<String> = when (json) {
        is JsonObject -> json.entries.flatMap { (key, value) ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            setOf(path) + fieldPaths(value, path)
        }.toSet()
        is JsonArray -> json.flatMap { fieldPaths(it, "$prefix[]") + "$prefix[]" }.toSet()
        else -> emptySet()
    }

    /**
     * Maps to free keys (food groups, phases, symptoms) are checked as `{}`: their keys must be known
     * enum names, so no free text can slip in.
     */
    fun normalisedPaths(json: JsonElement): Set<String> = fieldPaths(json).map { path ->
        path.split('.').joinToString(".") { part -> if (part.removeSuffix("[]") in FREE_KEYS) "{}" + part.substring(part.removeSuffix("[]").length) else part }
    }.toSet()

    private val FREE_KEYS: Set<String> = ShopSection.entries.map { it.name }.toSet() +
        com.calore.tracker.domain.cycle.CyclePhase.entries.map { it.name } +
        com.calore.tracker.domain.cycle.Symptom.entries.map { it.name }
}
