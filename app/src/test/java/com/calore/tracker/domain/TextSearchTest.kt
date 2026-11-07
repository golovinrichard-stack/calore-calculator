package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals("cocka na kyselo", searchKey("Čočka na KYSELO"))
        assertTrue(matchesSearch("cocka", "Čočka na kyselo s vejcem"))
        assertTrue(matchesSearch("ČOČKA", "cocka"))
    }

    @Test
    fun everyWordMustAppearInSomeText() {
        assertTrue(matchesSearch("kure ryzi", "Kuře s rýží a brokolicí"))
        assertTrue(matchesSearch("chicken ryz", "Kuře s rýží", "Chicken and rice"))
        assertFalse(matchesSearch("kure losos", "Kuře s rýží"))
        assertTrue("an empty search matches everything", matchesSearch("  ", "anything"))
        assertTrue(matchesSearch("x", null, "x"))
    }
}
