package com.underwaterai.inference

import org.junit.Test
import org.junit.Assert.*
import com.underwaterai.enhance.model.FeatureExtensions
import com.underwaterai.enhance.model.LocationUtils

class FeatureExtensionsTest {

    @Test
    fun testInvasiveSpeciesDetection() {
        assertTrue(FeatureExtensions.checkInvasiveSpecies("Dangerous lionfish spotted"))
        assertTrue(FeatureExtensions.checkInvasiveSpecies("Green Crab"))
        assertFalse(FeatureExtensions.checkInvasiveSpecies("Clownfish"))
        assertFalse(FeatureExtensions.checkInvasiveSpecies("Sea Turtle"))
    }

    @Test
    fun testGPSConversion() {
        // Test basic coordinate logic conversion
        val converted = LocationUtils.convert(45.5)
        // 45.5 degrees -> 45 deg, 30 min, 0 sec
        assertEquals("45/1,30/1,0/1000", converted)
    }
}
