package com.calore.tracker.data.household

import android.os.Build
import com.calore.tracker.data.db.HouseholdDao
import com.calore.tracker.data.db.HouseholdDeviceEntity
import com.calore.tracker.data.db.HouseholdEntity
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.sync.ApplyOutcome
import com.calore.tracker.data.sync.SyncStore
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.household.HouseholdInvite
import com.calore.tracker.domain.newId
import com.calore.tracker.domain.sync.SyncOp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Clock

/** Where this phone stands (M11.2). */
sealed interface HouseholdState {
    data object None : HouseholdState

    /** In a household. [isHub]: this phone is the hub (and the host). [approved]: the host confirmed this phone. */
    data class In(
        val household: HouseholdEntity,
        val me: HouseholdDeviceEntity?,
        val devices: List<HouseholdDeviceEntity>,
        val isHub: Boolean,
        val approved: Boolean,
    ) : HouseholdState

    /** The database says "in a household" but this phone has no key (restored from a backup): scan the code again. */
    data class NeedsKey(val household: HouseholdEntity) : HouseholdState
}

/**
 * The household on this phone (M11.2): create, join, confirm, remove, leave, hand over the hub.
 * Every change to the household's phones is a write to a shared table, so it syncs like the rest.
 */
class HouseholdRepository(
    private val dao: HouseholdDao,
    private val sync: SyncStore,
    private val keys: HouseholdKeys,
    private val members: MemberRepository,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {
    val deviceId: String get() = keys.deviceId()

    val state: Flow<HouseholdState> = combine(dao.observeHousehold(), dao.observeDevices()) { household, devices ->
        when {
            household == null -> HouseholdState.None
            keys.key() == null -> HouseholdState.NeedsKey(household)
            else -> {
                val me = devices.firstOrNull { it.id == deviceId }
                HouseholdState.In(
                    household = household,
                    me = me,
                    devices = devices,
                    isHub = household.hubDeviceId == deviceId,
                    approved = household.hubDeviceId == deviceId || me?.approved == true,
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    /** "3 changes waiting for home Wi-Fi" (M11.10): recorded changes plus ops the hub hasn't confirmed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val waitingChanges: Flow<Int> = combine(dao.observeHousehold(), dao.observeDevices()) { household, devices ->
        if (household == null || household.hubDeviceId == deviceId) null else hubSeen(devices.firstOrNull { it.id == deviceId })
    }.distinctUntilChanged().flatMapLatest { hubSeen ->
        if (hubSeen == null) flowOf(0) else combine(dao.observeRecordedChanges(), dao.observeUnsent(deviceId, hubSeen)) { a, b -> a + b }
    }

    /** Local changes not yet sent (they start a sync 5 seconds later, M11.4). */
    val recordedChanges: Flow<Int> = dao.observeRecordedChanges()

    suspend fun current(): HouseholdState = withContext(Dispatchers.IO) {
        val household = dao.household() ?: return@withContext HouseholdState.None
        if (keys.key() == null) return@withContext HouseholdState.NeedsKey(household)
        val devices = dao.allDevices().filter { it.deletedAt == null }
        val me = devices.firstOrNull { it.id == deviceId }
        val isHub = household.hubDeviceId == deviceId
        HouseholdState.In(household, me, devices, isHub, isHub || me?.approved == true)
    }

    fun key(): ByteArray? = keys.key()

    var hubMode: Boolean
        get() = keys.hubMode
        set(on) {
            keys.hubMode = on
        }

    /** The host makes a household (M11.2): this phone becomes the hub; everything shared on it goes to the household. */
    suspend fun create(name: String): HouseholdInvite = withContext(Dispatchers.IO) {
        check(dao.household() == null) { "Already in a household" }
        val key = ByteArray(HouseholdInvite.KEY_BYTES).also(random::nextBytes)
        val now = clock.millis()
        val household = HouseholdEntity(newId(), name.trim(), deviceId, 1, now, now)
        keys.saveKey(key)
        sync.setRecording(true)
        sync.snapshotAll()
        dao.upsertHousehold(household)
        writeMyDevice(isHub = true, approved = true, sharesTargets = false)
        HouseholdInvite.of(household.id, household.name, deviceId, key)
    }

    /** The QR code the host shows (M11.2); null on other phones. */
    suspend fun invite(): HouseholdInvite? = withContext(Dispatchers.IO) {
        val household = dao.household()?.takeIf { it.hubDeviceId == deviceId } ?: return@withContext null
        val key = keys.key() ?: return@withContext null
        HouseholdInvite.of(household.id, household.name, deviceId, key)
    }

    /**
     * Joins the household in [invite] (M11.2), or takes the new key of this phone's household
     * (after the host removed someone). The host still has to confirm a new phone.
     */
    suspend fun join(invite: HouseholdInvite, sharesTargets: Boolean) = withContext(Dispatchers.IO) {
        val existing = dao.household()
        if (existing != null && existing.id != invite.householdId) error("Already in another household")
        keys.saveKey(invite.keyBytes())
        if (existing != null) {
            dao.upsertHousehold(existing.copy(keyVersion = existing.keyVersion + 1, updatedAt = clock.millis()))
            // After a restore this phone has a new device id: it needs its own row (and the hub's confirmation).
            if (dao.device(deviceId) == null) {
                sync.setRecording(true)
                writeMyDevice(isHub = false, approved = false)
            }
            return@withContext
        }
        val now = clock.millis()
        sync.setRecording(true)
        sync.snapshotAll()
        dao.upsertHousehold(HouseholdEntity(invite.householdId, invite.name, invite.hubDeviceId, 1, now, now))
        members.updateMe { it.copy(sharesTargets = sharesTargets) }
        writeMyDevice(isHub = false, approved = false, sharesTargets = sharesTargets)
    }

    /** This phone's row: the person's name and colour (and later their targets, if they allow it). */
    suspend fun writeMyDevice(isHub: Boolean? = null, approved: Boolean? = null, sharesTargets: Boolean? = null) {
        val me = members.ensureMe()
        val now = clock.millis()
        val existing = dao.device(deviceId)
        val shares = sharesTargets ?: existing?.sharesTargets ?: me.sharesTargets
        dao.upsertDevice(
            HouseholdDeviceEntity(
                id = deviceId,
                memberId = me.id,
                name = existing?.name ?: listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "Android" },
                memberName = me.name,
                colour = me.colour,
                isHub = isHub ?: existing?.isHub ?: false,
                approved = approved ?: existing?.approved ?: false,
                sharesTargets = shares,
                targets = if (shares) SharedTargetsCodec.encode(me) else null,
                lastSeenAt = existing?.lastSeenAt,
                seenVector = existing?.seenVector,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    suspend fun setSharesTargets(on: Boolean) {
        members.updateMe { it.copy(sharesTargets = on) }
        if (dao.household() != null) writeMyDevice(sharesTargets = on)
    }

    /** The host confirms a new phone. */
    suspend fun approve(deviceId: String) {
        val device = dao.device(deviceId) ?: return
        dao.upsertDevice(device.copy(approved = true, updatedAt = clock.millis()))
    }

    /**
     * The host removes a phone (M11.2): its row is marked removed and the household gets a new key,
     * so the removed phone can't sync any more. Returns the new code for the remaining phones.
     */
    suspend fun remove(deviceId: String): HouseholdInvite? = withContext(Dispatchers.IO) {
        val device = dao.device(deviceId) ?: return@withContext null
        val now = clock.millis()
        dao.upsertDevice(device.copy(deletedAt = now, updatedAt = now))
        val household = dao.household() ?: return@withContext null
        keys.saveKey(ByteArray(HouseholdInvite.KEY_BYTES).also(random::nextBytes))
        dao.upsertHousehold(household.copy(keyVersion = household.keyVersion + 1, updatedAt = now))
        invite()
    }

    /**
     * Hands the hub to another phone (M11.5). This phone keeps serving until that phone has synced
     * once more and so knows it is the hub ([afterHubSession]).
     */
    suspend fun handOver(toDeviceId: String) {
        val target = dao.device(toDeviceId) ?: return
        val now = clock.millis()
        dao.upsertDevice(target.copy(isHub = true, approved = true, updatedAt = now))
        dao.device(deviceId)?.let { dao.upsertDevice(it.copy(isHub = false, updatedAt = now)) }
    }

    /** Leaving (M11.2): the others keep what was shared; this phone keeps its data but stops syncing. */
    suspend fun leave() = withContext(Dispatchers.IO) {
        keys.clear()
        sync.reset()
        dao.deleteDevices()
        dao.deleteHousehold()
    }

    /** What the hub lets [deviceId] do. */
    suspend fun admission(deviceId: String): Admission {
        val device = dao.device(deviceId) ?: return Admission.WAITING
        return when {
            device.deletedAt != null -> Admission.REFUSED
            device.approved -> Admission.MEMBER
            else -> Admission.WAITING
        }
    }

    /** This phone's side of a sync. */
    fun endpoint(): SyncEndpoint = object : SyncEndpoint {
        override val deviceId: String get() = this@HouseholdRepository.deviceId
        override suspend fun prepare() {
            sync.flush(deviceId)
        }
        override suspend fun versionVector(): Map<String, Long> = sync.versionVector()
        override suspend fun opsFor(theirs: Map<String, Long>): List<SyncOp> = sync.opsFor(theirs, limit = Int.MAX_VALUE)
        override suspend fun apply(ops: List<SyncOp>): ApplyOutcome = sync.apply(deviceId, ops)
    }

    /** After a session with the hub: who is the hub now, and the other phones as people (members of kind HOUSEHOLD). */
    suspend fun afterPhoneSession(result: SessionResult) {
        // The hub had what it said in its HELLO, and now also everything this phone just sent.
        val mine = sync.versionVector()[deviceId] ?: 0L
        val hubHas = result.peerVector + (deviceId to maxOf(mine, result.peerVector[deviceId] ?: 0L))
        dao.device(deviceId)?.let { dao.markSeen(it.id, clock.millis(), Json.encodeToString(VECTOR, hubHas)) }
        val household = dao.household() ?: return
        val devices = dao.allDevices()
        val hub = devices.firstOrNull { it.isHub && it.deletedAt == null }
        if (hub != null && hub.id != household.hubDeviceId) dao.upsertHousehold(household.copy(hubDeviceId = hub.id, updatedAt = clock.millis()))
        mirrorPeople(devices)
        compact(mapOf(result.peerDeviceId to hubHas))
    }

    /** After a session on the hub: when the phone last synced, and the end of a hand-over once the new hub knows. */
    suspend fun afterHubSession(result: SessionResult) {
        val now = clock.millis()
        dao.markSeen(result.peerDeviceId, now, Json.encodeToString(VECTOR, result.peerVector))
        val household = dao.household() ?: return
        val devices = dao.allDevices()
        val peer = devices.firstOrNull { it.id == result.peerDeviceId }
        if (peer != null && peer.isHub && peer.deletedAt == null && household.hubDeviceId == deviceId) {
            dao.upsertHousehold(household.copy(hubDeviceId = peer.id, updatedAt = now))
            keys.hubMode = false
        }
        mirrorPeople(devices)
        val vectors = devices.filter { it.deletedAt == null && it.approved && it.id != deviceId }.associate { device ->
            device.id to (device.seenVector?.let { Json.decodeFromString(VECTOR, it) } ?: emptyMap())
        }
        if (vectors.isNotEmpty()) compact(vectors)
    }

    private suspend fun compact(peers: Map<String, Map<String, Long>>) {
        sync.compact(peers)
    }

    /** The highest of this phone's own ops the hub had seen at the last sync. */
    private fun hubSeen(me: HouseholdDeviceEntity?): Long =
        me?.seenVector?.let { Json.decodeFromString(VECTOR, it) }?.get(deviceId) ?: 0L

    /** The other phones' people, as members of kind HOUSEHOLD, so their names and colours show on boxes. */
    private suspend fun mirrorPeople(devices: List<HouseholdDeviceEntity>) {
        val me = members.ensureMe()
        for (device in devices) {
            if (device.id == deviceId || device.memberId == me.id) continue
            val existing = members.member(device.memberId)
            if (existing != null && existing.kind != MemberKind.HOUSEHOLD) continue
            val base = existing ?: Member(id = device.memberId, kind = MemberKind.HOUSEHOLD, name = device.memberName, colour = device.colour)
            val targets = if (device.sharesTargets) device.targets?.let(SharedTargetsCodec::decode) else null
            val updated = SharedTargetsCodec.applyTo(base.copy(name = device.memberName, colour = device.colour, sharesTargets = device.sharesTargets), targets)
            if (updated != existing || (device.deletedAt != null) != members.isRemoved(device.memberId)) {
                members.saveHousehold(updated, removed = device.deletedAt != null)
            }
        }
    }

    private companion object {
        val VECTOR = MapSerializer(String.serializer(), Long.serializer())
    }
}
