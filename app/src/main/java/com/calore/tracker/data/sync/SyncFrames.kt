package com.calore.tracker.data.sync

import com.calore.tracker.data.db.SyncScope
import com.calore.tracker.domain.sync.Hlc
import com.calore.tracker.domain.sync.SyncOp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A frame that can't be trusted: wrong key, changed bytes, wrong household, or too big. */
class BadFrameException(message: String) : Exception(message)

/**
 * The household connection's frames (M11.4): a 4-byte length, a 12-byte nonce, then the AES-256-GCM
 * ciphertext of a JSON message, with the household id as associated data. A frame that fails to
 * decrypt must close the connection.
 */
object SyncFrames {

    const val NONCE_BYTES = 12
    const val KEY_BYTES = 32
    private const val TAG_BITS = 128

    /** At most 1 MB per frame (M11.4). */
    const val MAX_FRAME_BYTES = 1 shl 20

    fun seal(key: ByteArray, householdId: String, plaintext: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(key.size == KEY_BYTES) { "The household key is 32 bytes" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(householdId.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        val length = NONCE_BYTES + ciphertext.size
        if (length > MAX_FRAME_BYTES) throw BadFrameException("Frame too big")
        return ByteBuffer.allocate(4 + length).putInt(length).put(nonce).put(ciphertext).array()
    }

    /** The plaintext of one whole frame, or [BadFrameException]. */
    fun open(key: ByteArray, householdId: String, frame: ByteArray): ByteArray {
        if (frame.size < 4 + NONCE_BYTES) throw BadFrameException("Frame too short")
        val buffer = ByteBuffer.wrap(frame)
        val length = buffer.int
        if (length != frame.size - 4 || length > MAX_FRAME_BYTES) throw BadFrameException("Bad length")
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        val ciphertext = ByteArray(length - NONCE_BYTES).also { buffer.get(it) }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(householdId.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw BadFrameException("Frame failed to decrypt")
        }
    }

    fun write(out: DataOutputStream, frame: ByteArray) {
        out.write(frame)
        out.flush()
    }

    /** Reads one frame; refuses lengths over the limit before reading them. */
    fun read(input: DataInputStream): ByteArray {
        val length = try {
            input.readInt()
        } catch (e: EOFException) {
            throw BadFrameException("Connection closed")
        }
        if (length < NONCE_BYTES || length > MAX_FRAME_BYTES) throw BadFrameException("Bad length")
        val rest = ByteArray(length).also { input.readFully(it) }
        return ByteBuffer.allocate(4 + length).putInt(length).put(rest).array()
    }
}

/** The connection's messages (M11.4): HELLO, OPS (at most 500 per batch) and ACK. */
@Serializable
sealed interface SyncMessage {
    @Serializable
    @kotlinx.serialization.SerialName("HELLO")
    data class Hello(val deviceId: String, val versionVector: Map<String, Long>) : SyncMessage

    @Serializable
    @kotlinx.serialization.SerialName("OPS")
    data class Ops(val ops: List<WireOp>) : SyncMessage

    @Serializable
    @kotlinx.serialization.SerialName("ACK")
    data object Ack : SyncMessage

    companion object {
        const val MAX_OPS_PER_BATCH = 500
        private val json = Json { ignoreUnknownKeys = false; classDiscriminator = "type" }

        fun encode(message: SyncMessage): ByteArray = json.encodeToString(serializer(), message).toByteArray(Charsets.UTF_8)

        fun decode(bytes: ByteArray): SyncMessage = json.decodeFromString(serializer(), bytes.decodeToString())
    }
}

/** An op on the wire. Only shared tables may ever be sent (M11.11). */
@Serializable
data class WireOp(
    val opId: String,
    val deviceId: String,
    val seq: Long,
    val hlc: String,
    val table: String,
    val rowId: String,
    val changes: Map<String, String?>,
) {
    fun toDomain(): SyncOp {
        if (table !in SyncScope.SHARED) throw BadFrameException("Not a shared table")
        return SyncOp(opId, deviceId, seq, Hlc.parse(hlc) ?: throw BadFrameException("Bad clock"), table, rowId, changes)
    }

    companion object {
        /** The one way to put an op on the wire: private tables are refused. */
        fun of(op: SyncOp): WireOp {
            require(op.table in SyncScope.SHARED) { "${op.table} is private and never syncs" }
            return WireOp(op.opId, op.deviceId, op.seq, op.hlc.encode(), op.table, op.rowId, op.changes)
        }
    }
}
