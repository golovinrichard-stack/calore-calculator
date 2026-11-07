package com.calore.tracker.data.household

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calore.tracker.data.db.CaloreDatabase
import com.calore.tracker.data.prefs.AppPrefs
import com.calore.tracker.data.prefs.SettingsStore
import com.calore.tracker.data.repository.MemberRepository
import com.calore.tracker.data.sync.SyncStore
import com.calore.tracker.domain.household.HouseholdInvite
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

/** M11.2: create, join, confirm, remove (new key), leave, on two databases standing in for two phones. */
@RunWith(AndroidJUnit4::class)
class HouseholdRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.systemUTC()
    private lateinit var hostDb: CaloreDatabase
    private lateinit var memberDb: CaloreDatabase
    private lateinit var host: HouseholdRepository
    private lateinit var member: HouseholdRepository

    private fun phone(db: CaloreDatabase, name: String): HouseholdRepository {
        val members = MemberRepository(
            memberDao = db.memberDao(),
            weightDao = db.weightDao(),
            legacySettings = SettingsStore(PreferenceDataStoreFactory.create { File(context.cacheDir, "test-$name-settings.preferences_pb") }),
            appPrefs = AppPrefs(PreferenceDataStoreFactory.create { File(context.cacheDir, "test-$name-app.preferences_pb") }),
            clock = clock,
        )
        return HouseholdRepository(db.householdDao(), SyncStore(db, clock), HouseholdKeys(context, "test-$name"), members, clock)
    }

    @Before
    fun setUp() {
        hostDb = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).addCallback(CaloreDatabase.SyncCallback).build()
        memberDb = Room.inMemoryDatabaseBuilder(context, CaloreDatabase::class.java).addCallback(CaloreDatabase.SyncCallback).build()
        host = phone(hostDb, "host-${System.nanoTime()}")
        member = phone(memberDb, "member-${System.nanoTime()}")
    }

    @After
    fun tearDown() = runBlocking {
        host.leave()
        member.leave()
        hostDb.close()
        memberDb.close()
    }

    @Test
    fun aPhoneJoinsWaitsForTheHostAndIsRemovedWithANewKey() = runBlocking {
        val invite = host.create("Novák family")
        assertEquals(invite, HouseholdInvite.decode(HouseholdInvite.encode(invite)))
        val hostState = host.current() as HouseholdState.In
        assertTrue(hostState.isHub)
        assertArrayEquals(invite.keyBytes(), host.key())

        member.join(invite, sharesTargets = true)
        val memberState = member.current() as HouseholdState.In
        assertFalse(memberState.isHub)
        assertFalse(memberState.approved)
        assertTrue(memberState.me!!.sharesTargets)
        assertNotNull(memberState.me!!.targets)

        // The member's phone row reaches the hub; the hub lets it wait, then the host confirms it.
        val endpointHost = host.endpoint()
        val endpointMember = member.endpoint()
        endpointMember.prepare()
        endpointHost.apply(endpointMember.opsFor(endpointHost.versionVector()).filter { it.table == "household_devices" })
        assertEquals(Admission.WAITING, host.admission(member.deviceId))
        host.approve(member.deviceId)
        assertEquals(Admission.MEMBER, host.admission(member.deviceId))

        val newInvite = host.remove(member.deviceId)!!
        assertEquals(Admission.REFUSED, host.admission(member.deviceId))
        assertFalse(invite.key == newInvite.key)
        assertArrayEquals(newInvite.keyBytes(), host.key())
    }

    @Test
    fun leavingKeepsTheDataButNotTheKey() = runBlocking {
        host.create("Home")
        host.leave()
        assertEquals(HouseholdState.None, host.current())
        assertEquals(null, host.key())
    }
}
