package com.calore.tracker.domain.household

import org.junit.Assert.assertEquals
import org.junit.Test

/** M11.8: the feed's lines. */
class ActivityFeedTest {

    private val now = 100L * 24 * 60 * 60 * 1000
    private val minute = 60_000L

    @Test
    fun namesAndCountsSurviveStorage() {
        assertEquals(listOf("Chili") to 6, ActivityFeed.decode(ActivityFeed.encode(listOf("Chili"), 6)))
        assertEquals(listOf("eggs", "milk") to null, ActivityFeed.decode(ActivityFeed.encode(listOf("eggs", "milk"))))
        assertEquals(listOf("#1 pick") to null, ActivityFeed.decode(ActivityFeed.encode(listOf("#1 pick"))))
    }

    @Test
    fun purchasesCloseTogetherBecomeOneLine() {
        val rows = listOf(
            ActivityRow("anna", "BOUGHT", "eggs", now - 10 * minute),
            ActivityRow("anna", "BOUGHT", "milk", now - 5 * minute),
            ActivityRow("tom", "COOKED", ActivityFeed.encode(listOf("Chili"), 6), now - 3 * minute),
            ActivityRow("anna", "BOUGHT", "bread", now - 3 * 60 * minute),
        )
        val feed = ActivityFeed.build(rows, now)
        assertEquals(3, feed.size)
        assertEquals(ActivityType.COOKED, feed[0].type)
        assertEquals(6, feed[0].count)
        assertEquals(listOf("eggs", "milk"), feed[1].names)
        assertEquals(listOf("bread"), feed[2].names)
    }

    @Test
    fun onlyTheLast30DaysAndKnownTypes() {
        val rows = listOf(
            ActivityRow("anna", "TOOK", "eggs", now - 31L * 24 * 60 * minute),
            ActivityRow("anna", "SOMETHING_NEW", "x", now - minute),
            ActivityRow("anna", "THREW_AWAY", "yoghurt", now - minute),
        )
        assertEquals(listOf(ActivityType.THREW_AWAY), ActivityFeed.build(rows, now).map { it.type })
    }
}
