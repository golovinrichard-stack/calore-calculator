package com.calore.tracker.data.sync

import com.calore.tracker.domain.sync.Hlc
import com.calore.tracker.domain.sync.SyncOp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom

/** M11.4 frames and M11.11 security cases. */
class SyncFramesTest {

    private val random = SecureRandom()
    private val key = ByteArray(32).also(random::nextBytes)
    private val household = "house-1"

    @Test
    fun aMessageGoesThroughAFrameUnchanged() {
        val op = SyncOp("o1", "A", 1, Hlc(1_000, 0, "A"), "shopping_items", "s1", mapOf("name" to "\"Eggs\"", "packs" to null))
        val message = SyncMessage.Ops(listOf(WireOp.of(op)))
        val out = ByteArrayOutputStream()
        SyncFrames.write(DataOutputStream(out), SyncFrames.seal(key, household, SyncMessage.encode(message)))
        val frame = SyncFrames.read(DataInputStream(ByteArrayInputStream(out.toByteArray())))
        val decoded = SyncMessage.decode(SyncFrames.open(key, household, frame)) as SyncMessage.Ops
        assertEquals(op, decoded.ops.single().toDomain())
        assertEquals(SyncMessage.Ack, SyncMessage.decode(SyncMessage.encode(SyncMessage.Ack)))
        assertEquals(SyncMessage.Hello("A", mapOf("A" to 3L)), SyncMessage.decode(SyncMessage.encode(SyncMessage.Hello("A", mapOf("A" to 3L)))))
    }

    @Test
    fun aWrongKeyAChangedFrameOrAnotherHouseholdIsRejected() {
        val frame = SyncFrames.seal(key, household, "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), SyncFrames.open(key, household, frame))
        // A removed member keeps the old key; after the key changes their frames fail.
        val newKey = ByteArray(32).also(random::nextBytes)
        assertThrows(BadFrameException::class.java) { SyncFrames.open(newKey, household, frame) }
        val changed = frame.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(BadFrameException::class.java) { SyncFrames.open(key, household, changed) }
        assertThrows(BadFrameException::class.java) { SyncFrames.open(key, "house-2", frame) }
        assertThrows(BadFrameException::class.java) { SyncFrames.open(key, household, frame.copyOf(10)) }
    }

    @Test
    fun framesOverOneMegabyteAreRefused() {
        assertThrows(BadFrameException::class.java) { SyncFrames.seal(key, household, ByteArray(SyncFrames.MAX_FRAME_BYTES)) }
        val tooLong = DataInputStream(ByteArrayInputStream(byteArrayOf(0x7f, 0, 0, 0)))
        assertThrows(BadFrameException::class.java) { SyncFrames.read(tooLong) }
    }

    @Test
    fun privateTablesNeverGoOnTheWire() {
        for (table in listOf("diary_entries", "weight_entries", "cycle_days", "supplements", "research_reports")) {
            val op = SyncOp("o", "A", 1, Hlc(1, 0, "A"), table, "r", emptyMap())
            assertThrows(IllegalArgumentException::class.java) { WireOp.of(op) }
            assertThrows(BadFrameException::class.java) { WireOp("o", "A", 1, Hlc(1, 0, "A").encode(), table, "r", emptyMap()).toDomain() }
        }
    }
}
