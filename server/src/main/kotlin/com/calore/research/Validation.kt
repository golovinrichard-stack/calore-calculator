package com.calore.research

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.Base64
import java.util.UUID

/**
 * What the server accepts (M10.6): exactly the fields of policy appendix A, with values in range.
 * Anything unknown, missing or out of range is refused, so nothing else can be stored.
 */
object Validation {

    const val MAX_BODY_BYTES = 16 * 1024

    val CATEGORIES = setOf("NUTRITION", "BODY", "CYCLE", "SUPPLEMENTS")
    val AGE_BANDS = setOf("18-24", "25-29", "30-34", "35-39", "40-44", "45-49", "50-54", "55-59", "60-64", "65+")
    val SEXES = setOf("MALE", "FEMALE")
    val REGIONS = setOf("CZ-10", "CZ-20", "CZ-31", "CZ-32", "CZ-41", "CZ-42", "CZ-51", "CZ-52", "CZ-53", "CZ-63", "CZ-64", "CZ-71", "CZ-72", "CZ-80")
    val DIETS = setOf("OMNIVORE", "VEGETARIAN", "VEGAN", "PESCATARIAN", "NO_PORK")
    val GOALS = setOf("CUT", "MAINTAIN", "BUILD", "RECOMP")
    val ACTIVITIES = setOf("SEDENTARY", "LIGHT", "MODERATE", "ACTIVE", "VERY_ACTIVE")
    val SECTIONS = setOf(
        "VEGETABLES", "FRUIT", "MEAT", "FISH", "DAIRY", "EGGS", "BAKERY", "GRAINS", "LEGUMES", "CANNED", "FROZEN",
        "SPICES_SAUCES", "OILS", "SNACKS", "DRINKS", "SUPPLEMENTS", "OTHER",
    )
    val BMI_BANDS = setOf("<18.5", "18.5-24.9", "25-29.9", "30-34.9", ">=35")
    val CONTRACEPTION = setOf("NONE", "HORMONAL", "COPPER_IUD")
    val PHASES = setOf("PERIOD", "FOLLICULAR", "OVULATION", "LUTEAL")
    val SYMPTOMS = setOf("CRAMPS", "BLOATING", "CRAVINGS", "LOW_MOOD", "LOW_ENERGY", "POOR_SLEEP", "HEADACHE")
    val SUPPLEMENT_KINDS = setOf("VITAMIN_D", "IRON", "MAGNESIUM", "CREATINE", "PROTEIN_POWDER", "OMEGA_3", "MULTIVITAMIN", "OTHER")

    private val ISO_WEEK = Regex("""\d{4}-W(0[1-9]|[1-4]\d|5[0-3])""")
    private val APP_VERSION = Regex("""[0-9A-Za-z.+-]{1,32}""")
    private val BODY_FAT_BAND = Regex("""(\d|[1-6]\d)-(\d|[1-6]\d)""")

    /** A problem with a body; the text says which field, never echoes the value. */
    class Invalid(message: String) : Exception(message)

    data class Registration(val participantId: String, val secret: String, val consentVersion: Int, val categories: Set<String>, val profile: JsonObject)

    data class Update(val consentVersion: Int, val categories: Set<String>, val profile: JsonObject)

    data class Report(val reportId: String, val participantId: String, val isoWeek: String, val categories: Set<String>, val json: JsonObject)

    fun registration(json: JsonElement): Registration {
        val o = obj(json, "registration")
        exactly(o, setOf("participantId", "secret", "consentVersion", "categories", "profile"), "registration")
        val secret = string(o, "secret")
        val bytes = runCatching { Base64.getDecoder().decode(secret) }.getOrNull()
        if (bytes == null || bytes.size != 32) throw Invalid("secret")
        return Registration(uuid(o, "participantId"), secret, int(o, "consentVersion", 1..100), categories(o), profile(o))
    }

    fun update(json: JsonElement): Update {
        val o = obj(json, "update")
        exactly(o, setOf("consentVersion", "categories", "profile"), "update")
        return Update(int(o, "consentVersion", 1..100), categories(o), profile(o))
    }

    /** A weekly report; [agreed] are the participant's categories: other sections are refused. */
    fun report(json: JsonElement, participantId: String, agreed: Set<String>): Report {
        val o = obj(json, "report")
        val always = setOf("reportId", "participantId", "reportVersion", "isoWeek", "appVersion")
        val sections = o.keys - always
        if (!o.keys.containsAll(always)) throw Invalid("report: missing fields")
        val categories = sections.map { it.uppercase() }.toSet()
        if (!CATEGORIES.containsAll(categories) || sections.any { it != it.lowercase() }) throw Invalid("report: unknown fields")
        if (!agreed.containsAll(categories)) throw Invalid("report: a category that wasn't agreed to")
        if (uuid(o, "participantId") != participantId) throw Invalid("participantId")
        int(o, "reportVersion", 1..1)
        val week = string(o, "isoWeek")
        if (!ISO_WEEK.matches(week)) throw Invalid("isoWeek")
        if (!APP_VERSION.matches(string(o, "appVersion"))) throw Invalid("appVersion")
        o["nutrition"]?.let { nutrition(obj(it, "nutrition")) }
        o["body"]?.let { body(obj(it, "body")) }
        o["cycle"]?.let { cycle(obj(it, "cycle")) }
        o["supplements"]?.let { supplements(obj(it, "supplements")) }
        return Report(uuid(o, "reportId"), participantId, week, categories, o)
    }

    private fun nutrition(o: JsonObject) {
        exactly(
            o,
            setOf(
                "countedDays", "avgKcal", "avgProteinG", "avgCarbsG", "avgFatG", "avgFiberG", "avgSugarG", "avgMealsPerDay",
                "targetKcal", "kcalShareBySection", "prepBoxSharePct", "quickAddSharePct", "planAdherencePct", "boxesWasted",
            ),
            "nutrition",
        )
        int(o, "countedDays", 3..7)
        int(o, "avgKcal", 0..20_000)
        listOf("avgProteinG", "avgCarbsG", "avgFatG", "avgFiberG", "avgSugarG").forEach { int(o, it, 0..2_000) }
        number(o, "avgMealsPerDay", 0.0..20.0)
        int(o, "targetKcal", 0..20_000)
        val shares = obj(o["kcalShareBySection"] ?: throw Invalid("kcalShareBySection"), "kcalShareBySection")
        shares.forEach { (key, _) ->
            if (key !in SECTIONS) throw Invalid("kcalShareBySection")
            int(shares, key, 0..100)
        }
        int(o, "prepBoxSharePct", 0..100)
        int(o, "quickAddSharePct", 0..100)
        nullableInt(o, "planAdherencePct", 0..100)
        int(o, "boxesWasted", 0..1_000)
    }

    private fun body(o: JsonObject) {
        exactly(o, setOf("bmiBand", "trendChangeKg", "bodyFatBand"), "body")
        nullableString(o, "bmiBand")?.let { if (it !in BMI_BANDS) throw Invalid("bmiBand") }
        if (o["trendChangeKg"] !is JsonNull) number(o, "trendChangeKg", -20.0..20.0)
        nullableString(o, "bodyFatBand")?.let { if (!BODY_FAT_BAND.matches(it)) throw Invalid("bodyFatBand") }
    }

    private fun cycle(o: JsonObject) {
        exactly(o, setOf("cycles"), "cycle")
        val cycles = o["cycles"] as? JsonArray ?: throw Invalid("cycles")
        if (cycles.size !in 1..10) throw Invalid("cycles")
        for (c in cycles) {
            val one = obj(c, "cycles[]")
            exactly(one, setOf("cycleLengthDays", "periodLengthDays", "contraception", "symptomDaysByPhase", "avgKcalByPhase"), "cycles[]")
            int(one, "cycleLengthDays", 10..120)
            nullableInt(one, "periodLengthDays", 1..30)
            if (string(one, "contraception") !in CONTRACEPTION) throw Invalid("contraception")
            val symptoms = obj(one["symptomDaysByPhase"] ?: throw Invalid("symptomDaysByPhase"), "symptomDaysByPhase")
            symptoms.forEach { (phase, counts) ->
                if (phase !in PHASES) throw Invalid("symptomDaysByPhase")
                val byPhase = obj(counts, "symptomDaysByPhase")
                byPhase.keys.forEach { if (it !in SYMPTOMS) throw Invalid("symptomDaysByPhase") else int(byPhase, it, 0..120) }
            }
            val kcal = obj(one["avgKcalByPhase"] ?: throw Invalid("avgKcalByPhase"), "avgKcalByPhase")
            kcal.keys.forEach { if (it !in PHASES) throw Invalid("avgKcalByPhase") else int(kcal, it, 0..20_000) }
        }
    }

    private fun supplements(o: JsonObject) {
        exactly(o, setOf("kinds"), "supplements")
        val kinds = o["kinds"] as? JsonArray ?: throw Invalid("kinds")
        if (kinds.size !in 1..SUPPLEMENT_KINDS.size) throw Invalid("kinds")
        for (k in kinds) {
            val one = obj(k, "kinds[]")
            exactly(one, setOf("kind", "adherencePct"), "kinds[]")
            if (string(one, "kind") !in SUPPLEMENT_KINDS) throw Invalid("kind")
            int(one, "adherencePct", 0..100)
        }
    }

    private fun categories(o: JsonObject): Set<String> {
        val list = (o["categories"] as? JsonArray ?: throw Invalid("categories")).map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw Invalid("categories") }
        if (list.isEmpty() || list.toSet().size != list.size || !CATEGORIES.containsAll(list)) throw Invalid("categories")
        return list.toSet()
    }

    private fun profile(o: JsonObject): JsonObject {
        val p = obj(o["profile"] ?: throw Invalid("profile"), "profile")
        exactly(p, setOf("ageBand", "sex", "region", "diet", "goalType", "activity"), "profile")
        fun oneOf(key: String, allowed: Set<String>, nullable: Boolean) {
            val v = if (nullable) nullableString(p, key) else string(p, key)
            if (v != null && v !in allowed) throw Invalid(key)
        }
        oneOf("ageBand", AGE_BANDS, nullable = true)
        oneOf("sex", SEXES, nullable = true)
        oneOf("region", REGIONS, nullable = true)
        oneOf("diet", DIETS, nullable = false)
        oneOf("goalType", GOALS, nullable = true)
        oneOf("activity", ACTIVITIES, nullable = true)
        return p
    }

    // --- Small helpers ---

    private fun obj(e: JsonElement, name: String): JsonObject = e as? JsonObject ?: throw Invalid(name)

    private fun exactly(o: JsonObject, keys: Set<String>, name: String) {
        if (o.keys != keys) throw Invalid("$name: unknown or missing fields")
    }

    private fun string(o: JsonObject, key: String): String =
        (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw Invalid(key)

    private fun nullableString(o: JsonObject, key: String): String? {
        val v = o[key] ?: throw Invalid(key)
        return if (v is JsonNull) null else string(o, key)
    }

    private fun uuid(o: JsonObject, key: String): String {
        val v = string(o, key)
        if (runCatching { UUID.fromString(v) }.getOrNull()?.toString() != v.lowercase()) throw Invalid(key)
        return v.lowercase()
    }

    private fun int(o: JsonObject, key: String, range: IntRange): Int {
        val p = o[key] as? JsonPrimitive ?: throw Invalid(key)
        val v = p.takeIf { !it.isString }?.intOrNull ?: throw Invalid(key)
        if (v !in range) throw Invalid(key)
        return v
    }

    private fun nullableInt(o: JsonObject, key: String, range: IntRange): Int? {
        val v = o[key] ?: throw Invalid(key)
        return if (v is JsonNull) null else int(o, key, range)
    }

    private fun number(o: JsonObject, key: String, range: ClosedFloatingPointRange<Double>): Double {
        val p = o[key] as? JsonPrimitive ?: throw Invalid(key)
        val v = p.takeIf { !it.isString }?.doubleOrNull ?: throw Invalid(key)
        if (v !in range) throw Invalid(key)
        return v
    }
}
