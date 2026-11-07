package com.calore.tracker.domain.household

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M11.2: the QR code's content. */
class HouseholdInviteTest {

    private val key = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun anInviteSurvivesTheQrCode() {
        val invite = HouseholdInvite.of("h1", "Novák family", "hub-device", key)
        val text = HouseholdInvite.encode(invite)
        assertTrue(text.contains("\"v\":1"))
        val back = HouseholdInvite.decode(text)!!
        assertEquals(invite, back)
        assertEquals(key.toList(), back.keyBytes().toList())
    }

    @Test
    fun otherCodesAreNotInvites() {
        assertNull(HouseholdInvite.decode("8594001234567"))
        assertNull(HouseholdInvite.decode("https://example.com"))
        assertNull(HouseholdInvite.decode("{}"))
        val good = HouseholdInvite.of("h1", "Home", "hub", key)
        assertNull(HouseholdInvite.decode(HouseholdInvite.encode(good.copy(v = 2))))
        assertNull(HouseholdInvite.decode(HouseholdInvite.encode(good.copy(key = "c2hvcnQ="))))
        assertNull(HouseholdInvite.decode(HouseholdInvite.encode(good.copy(key = "not base64!"))))
        assertNull(HouseholdInvite.decode(HouseholdInvite.encode(good.copy(name = " "))))
        assertNull(HouseholdInvite.decode(HouseholdInvite.encode(good.copy(householdId = ""))))
    }

    @Test
    fun theNetworkSeesOnlyAHash() {
        val hash = HouseholdInvite.discoveryHash("h1")
        assertEquals(16, hash.length)
        assertEquals(hash, HouseholdInvite.discoveryHash("h1"))
        assertNotEquals(hash, HouseholdInvite.discoveryHash("h2"))
        assertTrue(!hash.contains("h1"))
    }
}
