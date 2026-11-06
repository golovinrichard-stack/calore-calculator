package com.calore.tracker.data.repository

import com.calore.tracker.data.db.MemberDao
import com.calore.tracker.data.db.WeightDao
import com.calore.tracker.data.db.WeightEntryEntity
import com.calore.tracker.data.db.toDomain
import com.calore.tracker.data.db.toEntity
import com.calore.tracker.data.db.toStored
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.birthDateFromAge
import com.calore.tracker.domain.goalRatePct
import com.calore.tracker.domain.goalType
import com.calore.tracker.domain.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

/** People with targets: the phone's user (ME) and people they cook for (MANAGED). */
class MemberRepository(
    private val memberDao: MemberDao,
    private val weightDao: WeightDao,
    private val legacySettings: SettingsStore,
    private val appPrefs: AppPrefs,
    private val clock: Clock,
) {
    private val meLock = Mutex()

    /**
     * The phone's user. Until [ensureMe] has created the row on the very first start, this emits
     * a stand-in with default values (and an empty id), so screens never wait for it.
     */
    fun me(): Flow<Member> = memberDao.observeMe()
        .map { it?.toDomain() ?: PLACEHOLDER_ME }
        .distinctUntilChanged()

    fun managed(): Flow<List<Member>> = memberDao.observeManaged().map { rows -> rows.map { it.toDomain() } }

    /** Saves [member] and stamps `updatedAt`. */
    suspend fun save(member: Member) {
        memberDao.upsert(member.copy(updatedAt = clock.millis()).toEntity())
    }

    /** Changes the phone's user and returns the saved result. */
    suspend fun updateMe(transform: (Member) -> Member): Member {
        val me = ensureMe()
        val updated = transform(me).copy(id = me.id, kind = MemberKind.ME, createdAt = me.createdAt, updatedAt = clock.millis())
        memberDao.upsert(updated.toEntity())
        return updated
    }

    /**
     * Returns the ME row, creating it on the first start after installing or updating (M1.1).
     * An update from 0.x moves the old goals and calculator inputs into it:
     * - sex, height, activity, macro split and goals as they were (macro mode SPLIT, so the
     *   targets stay exactly the same);
     * - age becomes a birth date on 1 July, which the user is asked to confirm;
     * - lose / gain become cut 0.5 % / build 0.25 % (A3);
     * - the calculator's weight becomes today's weigh-in, only if no weight was logged yet.
     * The old settings are cleared only after the row is saved. Safe to call any number of times.
     */
    suspend fun ensureMe(): Member = meLock.withLock {
        memberDao.me()?.let { existing ->
            // A crash between saving the row and clearing the old keys must not leave them behind.
            if (legacySettings.legacyProfile().hasAnyValue) legacySettings.clear()
            return@withLock existing.toDomain()
        }

        val legacy = legacySettings.legacyProfile()
        val inputs = legacy.inputs
        val existingUser = legacy.hasAnyValue || memberDao.hasLoggedData()
        val today = LocalDate.now(clock)
        val now = clock.millis()

        val me = Member.newMe(id = newId(), name = "", now = now).copy(
            sex = inputs.sex,
            birthDate = inputs.ageYears?.let { birthDateFromAge(it, today) },
            heightCm = inputs.heightCm,
            activity = inputs.activity,
            goalType = inputs.weightGoal?.goalType,
            goalRatePct = inputs.weightGoal?.goalRatePct,
            macroMode = if (legacy.hasAnyValue) MacroMode.SPLIT else MacroMode.AUTO,
            split = if (legacy.hasAnyValue) inputs.split else null,
            targets = legacy.goals,
        )

        val legacyWeight = inputs.weightKg
        if (legacyWeight != null && weightDao.count() == 0) {
            weightDao.upsert(WeightEntryEntity(date = today.toStored(), kg = legacyWeight, uuid = newId()))
        }
        appPrefs.markProfileMoved(existingUser = existingUser, birthDateToConfirm = inputs.ageYears != null)
        memberDao.upsert(me.toEntity())
        legacySettings.clear()
        me
    }

    private companion object {
        val PLACEHOLDER_ME = Member.newMe(id = "", name = "", now = 0)
    }
}
