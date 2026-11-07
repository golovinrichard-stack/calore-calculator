package com.calore.tracker.domain.household

/** One kind of line in the activity feed (M11.8). */
enum class ActivityType { BOUGHT, COOKED, TOOK, THREW_AWAY, REQUESTED, ANSWERED }

/** A feed line: who did what with which items, and how many (e.g. boxes cooked). */
data class ActivityLine(val memberId: String, val type: ActivityType, val names: List<String>, val count: Int?, val at: Long)

/** A stored activity row, as the feed reads it. */
data class ActivityRow(val memberId: String, val type: String, val summary: String, val at: Long)

/**
 * The activity feed (M11.8). Rows hold item names (shared data anyway) and an optional count;
 * each phone makes the sentence in its own language. Purchases by the same person close together
 * become one line: "Anna bought eggs and milk".
 */
object ActivityFeed {
    const val DAYS = 30L
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val GROUP_MS = 60 * 60 * 1000L
    private const val COUNT_MARK = "\n#"

    fun encode(names: List<String>, count: Int? = null): String =
        names.joinToString("\n") { it.replace('\n', ' ') } + (count?.let { "$COUNT_MARK$it" } ?: "")

    fun decode(summary: String): Pair<List<String>, Int?> {
        val i = summary.lastIndexOf(COUNT_MARK)
        val count = if (i >= 0) summary.substring(i + COUNT_MARK.length).toIntOrNull() else null
        val names = (if (count != null) summary.substring(0, i) else summary).split('\n').filter { it.isNotBlank() }
        return names to count
    }

    /** The last 30 days, newest first, with purchases grouped. Unknown types (from a newer app) are skipped. */
    fun build(rows: List<ActivityRow>, nowMillis: Long): List<ActivityLine> {
        val since = nowMillis - DAYS * DAY_MS
        val lines = rows.filter { it.at >= since }.sortedByDescending { it.at }.mapNotNull { row ->
            val type = ActivityType.entries.firstOrNull { it.name == row.type } ?: return@mapNotNull null
            val (names, count) = decode(row.summary)
            ActivityLine(row.memberId, type, names, count, row.at)
        }
        val out = ArrayList<ActivityLine>()
        for (line in lines) {
            val last = out.lastOrNull()
            if (last != null && line.type == ActivityType.BOUGHT && last.type == ActivityType.BOUGHT &&
                last.memberId == line.memberId && last.at - line.at <= GROUP_MS
            ) {
                // Older purchase into the newer line; keep the order they were bought in.
                out[out.size - 1] = last.copy(names = (line.names + last.names).distinct())
            } else {
                out += line
            }
        }
        return out
    }
}
