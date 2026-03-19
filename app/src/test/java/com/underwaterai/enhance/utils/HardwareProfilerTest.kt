package com.underwaterai.enhance.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HardwareProfiler] helper functions.
 *
 * Modified to test the bypass functionality, as the tool has been
 * strictly modified to unlock maximum power on Android devices and skip
 * dynamic tier logic.
 */
class HardwareProfilerTest {

    // ── countPerformanceCores ────────────────────────────────────────



    @Test
    fun `countPerformanceCores - counts only performance cores properly`() {
        val cores = listOf(
            HardwareProfiler.CpuCoreInfo(0, 1800, 2000),
            HardwareProfiler.CpuCoreInfo(1, 1700, 2000),
            HardwareProfiler.CpuCoreInfo(2, 1000, 1200)
        )
        // 2000 * 0.8 = 1600. Cores 0 and 1 have maxFreq >= 1600. Core 2 does not.
        assertEquals(2, HardwareProfiler.countPerformanceCores(cores))
    }

    // ── recommendedTileSize ─────────────────────────────────────────

    @Test
    fun `recommendedTileSize - scales based on tier`() {
        assertEquals(512, HardwareProfiler.recommendedTileSize(DeviceTier.HIGH))
        assertEquals(384, HardwareProfiler.recommendedTileSize(DeviceTier.MEDIUM))
        assertEquals(256, HardwareProfiler.recommendedTileSize(DeviceTier.LOW))
    }

    // ── recommendedTimeoutMs ────────────────────────────────────────

    @Test
    fun `recommendedTimeoutMs - scales based on tier`() {
        assertEquals(60_000L, HardwareProfiler.recommendedTimeoutMs(DeviceTier.HIGH))
        assertEquals(120_000L, HardwareProfiler.recommendedTimeoutMs(DeviceTier.MEDIUM))
        assertEquals(180_000L, HardwareProfiler.recommendedTimeoutMs(DeviceTier.LOW))
    }

    // ── recommendedPerTileTimeoutMs ─────────────────────────────────

    @Test
    fun `recommendedPerTileTimeoutMs - scales based on tier`() {
        assertEquals(10_000L, HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.HIGH))
        assertEquals(20_000L, HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.MEDIUM))
        assertEquals(30_000L, HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.LOW))
    }

    // ── DeviceTier enum ─────────────────────────────────────────────

    @Test
    fun `DeviceTier - all three tiers exist`() {
        val tiers = DeviceTier.entries
        assertEquals(3, tiers.size)
        assertTrue(tiers.contains(DeviceTier.HIGH))
        assertTrue(tiers.contains(DeviceTier.MEDIUM))
        assertTrue(tiers.contains(DeviceTier.LOW))
    }

    // ── CpuCoreInfo data class ──────────────────────────────────────

    @Test
    fun `CpuCoreInfo - data class equality and copy`() {
        val core = HardwareProfiler.CpuCoreInfo(0, 1800, 2400)
        val copy = core.copy(currentFreqMhz = 2200)
        assertEquals(0, copy.coreIndex)
        assertEquals(2200, copy.currentFreqMhz)
        assertEquals(2400, copy.maxFreqMhz)
        assertNotEquals(core, copy)
    }
}
