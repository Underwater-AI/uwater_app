package com.underwaterai.inference

import com.underwaterai.enhance.model.MarineResearchServices
import org.junit.Assert.*
import org.junit.Test

class MarineResearchServicesTest {

    @Test
    fun testDepthEstimation() {
        val shallowDepth = MarineResearchServices.estimateDepthMeters(200, 100, 50)
        val veryDeep = MarineResearchServices.estimateDepthMeters(10, 50, 150)
        assertTrue(veryDeep > shallowDepth)
    }

    @Test
    fun testCoralBleaching() {
        assertEquals("Bleached_Alert", MarineResearchServices.assessCoralHealth(220, 230, 240))
        assertEquals("Healthy", MarineResearchServices.assessCoralHealth(50, 150, 80))
    }

    @Test
    fun testPlanktonTracking() {
        assertEquals(0, MarineResearchServices.processPlanktonMicroscopy(1f, 100))
        assertTrue(MarineResearchServices.processPlanktonMicroscopy(20f, 100) > 0)
    }

    @Test
    fun testAcousticSync() {
        val result = MarineResearchServices.syncAcousticData(ByteArray(512), 12345L)
        assertTrue(result.contains("512 bytes"))
        assertTrue(result.contains("12345"))
    }
}
