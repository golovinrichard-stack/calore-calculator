package com.calore.tracker.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StamperTest {

    private data class Row(
        val name: String,
        override val createdAt: Long = 0,
        override val updatedAt: Long = 0,
        override val deletedAt: Long? = null,
    ) : Stamped<Row> {
        override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
            copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
    }

    private fun stamperAt(millis: Long) = Stamper(Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC))

    @Test
    fun newRowsAreCreatedAndUpdatedNow() {
        val row = stamperAt(1_000).created(Row("chili"))
        assertEquals(1_000, row.createdAt)
        assertEquals(1_000, row.updatedAt)
        assertNull(row.deletedAt)
        assertFalse(row.isDeleted)
    }

    @Test
    fun updatesMoveOnlyUpdatedAt() {
        val row = stamperAt(1_000).created(Row("chili"))
        val changed = stamperAt(5_000).updated(row.copy(name = "chili con carne"))
        assertEquals(1_000, changed.createdAt)
        assertEquals(5_000, changed.updatedAt)
        assertEquals("chili con carne", changed.name)
    }

    @Test
    fun softDeleteAndRestore() {
        val row = stamperAt(1_000).created(Row("chili"))
        val deleted = stamperAt(2_000).softDeleted(row)
        assertTrue(deleted.isDeleted)
        assertEquals(2_000L, deleted.deletedAt)
        assertEquals(2_000, deleted.updatedAt)

        val restored = stamperAt(3_000).restored(deleted)
        assertNull(restored.deletedAt)
        assertEquals(3_000, restored.updatedAt)
        assertEquals(1_000, restored.createdAt)
    }

    @Test
    fun purgeCutoffIsThirtyDaysBack() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(10 * day, stamperAt(40 * day).purgeBefore())
        assertEquals("deletedAt IS NULL", SoftDelete.NOT_DELETED)
    }
}
