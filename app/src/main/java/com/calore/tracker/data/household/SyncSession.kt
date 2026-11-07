package com.calore.tracker.data.household

import com.calore.tracker.data.sync.ApplyOutcome
import com.calore.tracker.data.sync.BadFrameException
import com.calore.tracker.data.sync.SyncFrames
import com.calore.tracker.data.sync.SyncMessage
import com.calore.tracker.data.sync.WireOp
import com.calore.tracker.domain.sync.SyncOp
import kotlinx.serialization.SerializationException
import java.io.DataInputStream
import java.io.DataOutputStream

/** One side of a sync: this phone's ops, as the session needs them. */
interface SyncEndpoint {
    val deviceId: String

    /** Turns recorded local changes into ops, so they go along. */
    suspend fun prepare()
    suspend fun versionVector(): Map<String, Long>
    suspend fun opsFor(theirs: Map<String, Long>): List<SyncOp>
    suspend fun apply(ops: List<SyncOp>): ApplyOutcome
}

/** What the hub lets a connecting phone do (M11.2). */
enum class Admission {
    /** A member the host confirmed: everything shared goes both ways. */
    MEMBER,

    /** A phone that scanned the code and waits for the host: only its own phone row goes in, only phone rows come back. */
    WAITING,

    /** Removed from the household. */
    REFUSED,
}

/** How a session ended. */
data class SessionResult(
    val peerDeviceId: String,
    val peerVector: Map<String, Long>,
    val sent: Int,
    val received: ApplyOutcome?,
    val admission: Admission,
)

/**
 * The conversation between a phone and the hub (M11.4), in sealed frames:
 *
 * 1. phone → hub `HELLO {deviceId, versionVector}`; hub → phone `HELLO` (or the connection closes when refused);
 * 2. phone → hub the ops the hub is missing, in batches of at most 500, each answered with `ACK`, then an empty `OPS`;
 * 3. hub → phone the same the other way.
 *
 * Any frame that fails to open, or any message out of order, ends the session ([BadFrameException]).
 */
class SyncSession(private val key: ByteArray, private val householdId: String) {

    private fun send(out: DataOutputStream, message: SyncMessage) =
        SyncFrames.write(out, SyncFrames.seal(key, householdId, SyncMessage.encode(message)))

    private fun receive(input: DataInputStream): SyncMessage = try {
        SyncMessage.decode(SyncFrames.open(key, householdId, SyncFrames.read(input)))
    } catch (e: SerializationException) {
        throw BadFrameException("Not a sync message")
    } catch (e: IllegalArgumentException) {
        throw BadFrameException("Not a sync message")
    }

    /** The phone's side. */
    suspend fun runPhone(input: DataInputStream, output: DataOutputStream, me: SyncEndpoint): SessionResult {
        me.prepare()
        send(output, SyncMessage.Hello(me.deviceId, me.versionVector()))
        val hello = receive(input) as? SyncMessage.Hello ?: throw BadFrameException("Expected HELLO")
        val sent = sendOps(input, output, me.opsFor(hello.versionVector))
        val received = receiveOps(input, output) { me.apply(it) }
        return SessionResult(hello.deviceId, hello.versionVector, sent, received, Admission.MEMBER)
    }

    /** The hub's side; [admit] decides what the connecting phone may do. */
    suspend fun runHub(
        input: DataInputStream,
        output: DataOutputStream,
        hub: SyncEndpoint,
        admit: suspend (deviceId: String) -> Admission,
    ): SessionResult {
        val hello = receive(input) as? SyncMessage.Hello ?: throw BadFrameException("Expected HELLO")
        val admission = admit(hello.deviceId)
        if (admission == Admission.REFUSED) return SessionResult(hello.deviceId, hello.versionVector, 0, null, admission)
        hub.prepare()
        send(output, SyncMessage.Hello(hub.deviceId, hub.versionVector()))
        val received = receiveOps(input, output) { ops ->
            val allowed = ops.filter { op ->
                // A phone's row is written only by that phone, or by the hub (confirming it, handing over).
                op.table != DEVICES || op.deviceId == op.rowId || op.deviceId == hub.deviceId
            }
            hub.apply(if (admission == Admission.MEMBER) allowed else allowed.filter { it.table == DEVICES && it.rowId == hello.deviceId })
        }
        val missing = hub.opsFor(hello.versionVector)
        val sent = sendOps(input, output, if (admission == Admission.MEMBER) missing else missing.filter { it.table == DEVICES })
        return SessionResult(hello.deviceId, hello.versionVector, sent, received, admission)
    }

    private fun sendOps(input: DataInputStream, output: DataOutputStream, ops: List<SyncOp>): Int {
        for (batch in ops.chunked(SyncMessage.MAX_OPS_PER_BATCH)) {
            send(output, SyncMessage.Ops(batch.map(WireOp::of)))
            if (receive(input) != SyncMessage.Ack) throw BadFrameException("Expected ACK")
        }
        send(output, SyncMessage.Ops(emptyList()))
        return ops.size
    }

    private suspend fun receiveOps(input: DataInputStream, output: DataOutputStream, apply: suspend (List<SyncOp>) -> ApplyOutcome): ApplyOutcome {
        var total: ApplyOutcome? = null
        while (true) {
            val message = receive(input) as? SyncMessage.Ops ?: throw BadFrameException("Expected OPS")
            if (message.ops.isEmpty()) break
            if (message.ops.size > SyncMessage.MAX_OPS_PER_BATCH) throw BadFrameException("Too many ops")
            val outcome = apply(message.ops.map(WireOp::toDomain))
            total = total?.let { ApplyOutcome(it.applied + outcome.applied, outcome.parked, it.conflicts + outcome.conflicts, it.rows + outcome.rows) } ?: outcome
            send(output, SyncMessage.Ack)
        }
        return total ?: ApplyOutcome(0, 0, emptyList(), emptyList())
    }

    companion object {
        const val DEVICES = "household_devices"
    }
}
