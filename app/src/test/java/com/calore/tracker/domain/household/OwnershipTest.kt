package com.calore.tracker.domain.household

import com.calore.tracker.domain.Visibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M11.6: labels and who may take what. */
class OwnershipTest {

    @Test
    fun someoneElsesMineLabelMeansAsking() {
        assertFalse(Ownership.canTake("anna", Visibility.MINE, me = "tom"))
        assertTrue(Ownership.canTake("anna", Visibility.SHARED, me = "tom"))
        assertTrue(Ownership.canTake("anna", Visibility.UP_FOR_GRABS, me = "tom"))
    }

    @Test
    fun myOwnAndMyKidsFoodCanBeTaken() {
        assertTrue(Ownership.canTake("tom", Visibility.MINE, me = "tom"))
        assertTrue(Ownership.canTake("kid", Visibility.MINE, me = "tom", managed = setOf("kid")))
        assertTrue(Ownership.canTake(null, Visibility.MINE, me = "tom"))
    }

    @Test
    fun onlyTheOwnerChangesTheLabel() {
        assertTrue(Ownership.canChangeLabel("tom", me = "tom"))
        assertTrue(Ownership.canChangeLabel(null, me = "tom"))
        assertTrue(Ownership.canChangeLabel("kid", me = "tom", managed = setOf("kid")))
        assertFalse(Ownership.canChangeLabel("anna", me = "tom"))
    }

    @Test
    fun upForGrabsShowsForEveryoneElse() {
        assertTrue(Ownership.showsAsUpForGrabs("anna", Visibility.UP_FOR_GRABS, me = "tom"))
        assertTrue(Ownership.showsAsUpForGrabs(null, Visibility.UP_FOR_GRABS, me = "tom"))
        assertFalse(Ownership.showsAsUpForGrabs("tom", Visibility.UP_FOR_GRABS, me = "tom"))
        assertFalse(Ownership.showsAsUpForGrabs("anna", Visibility.SHARED, me = "tom"))
    }
}
