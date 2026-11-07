package com.calore.tracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** M6.1: one 👍 / 👎 per person and recipe; changing and taking it back. */
@RunWith(AndroidJUnit4::class)
class RatingRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: CaloreDatabase
    private lateinit var ratings: RatingRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).build()
        ratings = RatingRepository(db.ratingDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun rateChangeAndTakeBack() = runTest {
        ratings.rate("me", "curry", 1)
        ratings.rate("kid", "curry", -1)
        assertEquals(mapOf("me" to mapOf("curry" to 1), "kid" to mapOf("curry" to -1)), ratings.all())

        ratings.rate("me", "curry", -1)
        assertEquals(-1, ratings.observe("me", "curry").first())
        assertEquals(1, db.ratingDao().all().count { it.memberId == "me" })

        ratings.rate("me", "curry", null)
        assertNull(ratings.observe("me", "curry").first())
        assertEquals(mapOf("kid" to mapOf("curry" to -1)), ratings.all())

        // Rating again brings the same row back.
        ratings.rate("me", "curry", 1)
        assertEquals(1, ratings.all()["me"]?.get("curry"))
    }
}
