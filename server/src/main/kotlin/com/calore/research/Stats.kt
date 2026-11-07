package com.calore.research

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import java.io.File

/** One row of the statistics export: a group and its averages. */
data class StatsRow(
    val week: String,
    val ageBand: String,
    val sex: String,
    val region: String,
    val participants: Int,
    val averages: Map<String, Double>,
)

/**
 * The statistics export (M10.6): CSV aggregates of nutrition by week, age band, sex and region.
 * Any group with fewer than [MIN_GROUP] people is left out, so only group statistics leave the database.
 */
object Stats {

    const val MIN_GROUP = 10

    val FIELDS = listOf("avgKcal", "avgProteinG", "avgCarbsG", "avgFatG", "avgFiberG", "avgSugarG")

    fun aggregate(rows: List<Pair<StoredReport, String>>): List<StatsRow> {
        data class Key(val week: String, val age: String, val sex: String, val region: String)
        val groups = HashMap<Key, MutableMap<String, JsonObject>>()
        for ((report, profileText) in rows) {
            val nutrition = Json.parseToJsonElement(report.payload).jsonObject["nutrition"] as? JsonObject ?: continue
            val profile = Json.parseToJsonElement(profileText).jsonObject
            fun p(key: String) = (profile[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "unknown"
            val key = Key(report.isoWeek, p("ageBand"), p("sex"), p("region"))
            // One report per person and week counts once.
            groups.getOrPut(key) { HashMap() }[report.participantId] = nutrition
        }
        return groups.filterValues { it.size >= MIN_GROUP }.map { (key, byPerson) ->
            StatsRow(
                key.week, key.age, key.sex, key.region, byPerson.size,
                FIELDS.associateWith { f -> byPerson.values.map { (it[f] as JsonPrimitive).double }.average() },
            )
        }.sortedWith(compareBy({ it.week }, { it.ageBand }, { it.sex }, { it.region }))
    }

    fun csv(rows: List<StatsRow>): String = buildString {
        append("week,age_band,sex,region,participants,").append(FIELDS.joinToString(",")).append('\n')
        for (r in rows) {
            append(listOf(r.week, r.ageBand, r.sex, r.region, r.participants.toString()).joinToString(","))
            FIELDS.forEach { append(',').append("%.1f".format(java.util.Locale.ROOT, r.averages.getValue(it))) }
            append('\n')
        }
    }

    fun export(store: Store, dir: File): File {
        dir.mkdirs()
        val file = File(dir, "nutrition-by-group.csv")
        file.writeText(csv(aggregate(store.reportsWithProfiles())))
        return file
    }
}
