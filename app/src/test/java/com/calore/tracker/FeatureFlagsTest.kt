package com.calore.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {

    @Test
    fun unfinishedFeaturesAreHiddenInReleaseBuilds() {
        assertFalse(FeatureFlags.isVisible(finished = false, debugBuild = false))
    }

    @Test
    fun unfinishedFeaturesShowInDebugBuilds() {
        assertTrue(FeatureFlags.isVisible(finished = false, debugBuild = true))
    }

    @Test
    fun finishedFeaturesAlwaysShow() {
        assertTrue(FeatureFlags.isVisible(finished = true, debugBuild = false))
        assertTrue(FeatureFlags.isVisible(finished = true, debugBuild = true))
    }
}
