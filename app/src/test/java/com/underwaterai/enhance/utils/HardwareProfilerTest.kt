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
    fun `countPerformanceCores - always returns availableProcessors for max power`() {
        val cores = listOf(
            HardwareProfiler.CpuCoreInfo(0, 1800, 2000),
            HardwareProfiler.CpuCoreInfo(1, 1700, 2000)
        )
        // Regardless of input, it should return available processors
        assertEquals(Runtime.getRuntime().availableProcessors(), HardwareProfiler.countPerformanceCores(cores))
    }

    @Test
    fun `countPerformanceCores - empty list falls back to availableProcessors`() {
        val result = HardwareProfiler.countPerformanceCores(emptyList())
        assertTrue("Should return at least 1 core", result >= 1)
        assertEquals(Runtime.getRuntime().availableProcessors(), result)
    }

    // ── recommendedTileSize ─────────────────────────────────────────

    @Test
    fun `recommendedTileSize - always returns 512 regardless of tier`() {
        assertEquals(512, HardwareProfiler.recommendedTileSize(DeviceTier.HIGH))
        assertEquals(512, HardwareProfiler.recommendedTileSize(DeviceTier.MEDIUM))
        assertEquals(512, HardwareProfiler.recommendedTileSize(DeviceTier.LOW))
    }

    // ── recommendedTimeoutMs ────────────────────────────────────────

    @Test
    fun `recommendedTimeoutMs - always returns Long MAX_VALUE regardless of tier`() {
        assertEquals(Long.MAX_VALUE, HardwareProfiler.recommendedTimeoutMs(DeviceTier.HIGH))
        assertEquals(Long.MAX_VALUE, HardwareProfiler.recommendedTimeoutMs(DeviceTier.MEDIUM))
        assertEquals(Long.MAX_VALUE, HardwareProfiler.recommendedTimeoutMs(DeviceTier.LOW))
    }

    // ── recommendedPerTileTimeoutMs ─────────────────────────────────

    @Test
    fun `recommendedPerTileTimeoutMs - always returns Long MAX_VALUE regardless of tier`() {
        assertEquals(Long.MAX_VALUE, HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.HIGH))
        assertEquals(Long.MAX_VALUE, HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.MEDIUM))
        assertEquals(Long.MAX_VALUE, HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.LOW))
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
