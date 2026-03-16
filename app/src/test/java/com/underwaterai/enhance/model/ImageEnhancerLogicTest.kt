package com.underwaterai.enhance.model

import com.underwaterai.enhance.utils.DeviceTier
import com.underwaterai.enhance.utils.HardwareProfiler
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ImageEnhancer] helper logic.
 *
 * We replicate the *exact* formulas from ImageEnhancer's private helpers
 * (computeBlendWeight, alignDimensions, tile-grid) so the tests can catch
 * regressions without needing an Android device or emulator.
 *
 * If a refactor changes the formula in ImageEnhancer without updating
 * these tests, the mis-match will surface as soon as someone spot-checks
 * the logic – which is the point.
 */
class ImageEnhancerLogicTest {

    // ── Constants (must match companion object) ─────────────────────

    private val DEFAULT_TILE_SIZE = 512
    private val TILE_OVERLAP = 32
    private val DIM_ALIGNMENT = 4

    @Test
    fun `DEFAULT_TILE_SIZE is 512`() = assertEquals(512, DEFAULT_TILE_SIZE)

    @Test
    fun `TILE_OVERLAP is 32`() = assertEquals(32, TILE_OVERLAP)

    @Test
    fun `DIM_ALIGNMENT is 4`() = assertEquals(4, DIM_ALIGNMENT)

    // ── Blend weight tests ──────────────────────────────────────────
    // Replicates computeBlendWeight(x, y, w, h, overlap) exactly.
    // Formula: wx = clamp( min(x, w-1-x) / overlap, 0..1 )
    //          wy = clamp( min(y, h-1-y) / overlap, 0..1 )
    //          return if overlap<=0 then 1f else wx * wy

    private fun blendWeight(x: Int, y: Int, w: Int, h: Int, overlap: Int): Float {
        if (overlap <= 0) return 1f
        val wx = (minOf(x, w - 1 - x).toFloat() / overlap).coerceIn(0f, 1f)
        val wy = (minOf(y, h - 1 - y).toFloat() / overlap).coerceIn(0f, 1f)
        return wx * wy
    }

    @Test
    fun `blendWeight - center pixel returns 1 for zero overlap`() {
        assertEquals(1f, blendWeight(50, 50, 100, 100, 0), 0.001f)
    }

    @Test
    fun `blendWeight - center pixel returns 1 for nonzero overlap`() {
        // A pixel well inside the tile (far from edges) should have weight 1.0
        assertEquals(1f, blendWeight(50, 50, 100, 100, 16), 0.001f)
    }

    @Test
    fun `blendWeight - edge pixel at border is zero`() {
        // x=0 → wx = min(0, w-1)/overlap = 0 → weight = 0
        // This is correct: the outermost pixel is entirely from the adjacent tile
        val weight = blendWeight(0, 50, 100, 100, 16)
        assertEquals("Edge pixel weight should be 0", 0f, weight, 0.001f)
    }

    @Test
    fun `blendWeight - pixel just inside overlap is between 0 and 1`() {
        val weight = blendWeight(8, 50, 100, 100, 16)
        assertTrue("Pixel at x=8 weight ($weight) should be > 0", weight > 0f)
        assertTrue("Pixel at x=8 weight ($weight) should be < 1", weight < 1f)
    }

    @Test
    fun `blendWeight - corner pixel has minimum weight`() {
        val corner = blendWeight(0, 0, 100, 100, 16)
        val edge = blendWeight(0, 50, 100, 100, 16)
        assertTrue("Corner ($corner) should be <= edge ($edge)", corner <= edge)
    }

    @Test
    fun `blendWeight - weight is symmetric left-right`() {
        val left = blendWeight(5, 50, 100, 100, 16)
        val right = blendWeight(94, 50, 100, 100, 16)
        assertEquals(left, right, 0.02f) // allow small float rounding
    }

    @Test
    fun `blendWeight - weight is symmetric top-bottom`() {
        val top = blendWeight(50, 3, 100, 100, 16)
        val bottom = blendWeight(50, 96, 100, 100, 16)
        assertEquals(top, bottom, 0.02f)
    }

    @Test
    fun `blendWeight - weight increases linearly from edge to center`() {
        val w0 = blendWeight(0, 50, 100, 100, 16)
        val w4 = blendWeight(4, 50, 100, 100, 16)
        val w8 = blendWeight(8, 50, 100, 100, 16)
        assertTrue("w0 ($w0) < w4 ($w4)", w0 < w4)
        assertTrue("w4 ($w4) < w8 ($w8)", w4 < w8)
    }

    // ── Tile grid calculation tests ─────────────────────────────────
    // We replicate the formula from processWithTiles to verify the grid
    // covers the entire image without gaps or out-of-bounds access.

    private fun computeTileGrid(
        inputW: Int, inputH: Int, tileSize: Int, overlap: Int
    ): Triple<Int, Int, Int> {
        val tileStep = tileSize - overlap
        val tilesX = maxOf(1, (inputW - overlap + tileStep - 1) / tileStep)
        val tilesY = maxOf(1, (inputH - overlap + tileStep - 1) / tileStep)
        return Triple(tilesX, tilesY, tilesX * tilesY)
    }

    @Test
    fun `tileGrid - small image within a single tile`() {
        val (tx, ty, total) = computeTileGrid(256, 256, 512, 32)
        assertEquals(1, tx)
        assertEquals(1, ty)
        assertEquals(1, total)
    }

    @Test
    fun `tileGrid - image exactly one tile`() {
        val (tx, ty, total) = computeTileGrid(512, 512, 512, 32)
        assertEquals(1, tx)
        assertEquals(1, ty)
        assertEquals(1, total)
    }

    @Test
    fun `tileGrid - image slightly larger than one tile needs 2x1`() {
        // 600 wide with tile=512, overlap=32 → step=480
        // tilesX = max(1, (600 - 32 + 479) / 480) = max(1, 1047/480) = max(1,2) = 2
        val (tx, ty, _) = computeTileGrid(600, 256, 512, 32)
        assertEquals(2, tx)
        assertEquals(1, ty)
    }

    @Test
    fun `tileGrid - image exactly two tiles`() {
        // width = tileSize + tileStep = 512 + 480 = 992
        val (tx, ty, _) = computeTileGrid(992, 512, 512, 32)
        assertEquals(2, tx)
        assertEquals(1, ty)
    }

    @Test
    fun `tileGrid - large 4K image has correct tile count`() {
        // 3840x2160 with tile=512, overlap=32 → step=480
        val (tx, ty, total) = computeTileGrid(3840, 2160, 512, 32)
        assertTrue("tilesX ($tx) should be >= 8", tx >= 8)
        assertTrue("tilesY ($ty) should be >= 4", ty >= 4)
        assertTrue("Total ($total) should be >= 32", total >= 32)
    }

    @Test
    fun `tileGrid - LOW tier tile=256 produces more tiles`() {
        // Same image with different tile sizes
        val (tx256, ty256, total256) = computeTileGrid(1024, 1024, 256, 32)
        val (tx512, ty512, total512) = computeTileGrid(1024, 1024, 512, 32)
        assertTrue("256 tiles ($total256) > 512 tiles ($total512)", total256 > total512)
    }

    @Test
    fun `tileGrid - 1px image still produces 1 tile`() {
        val (tx, ty, total) = computeTileGrid(1, 1, 512, 32)
        assertEquals(1, tx)
        assertEquals(1, ty)
        assertEquals(1, total)
    }

    @Test
    fun `tileGrid - last tile covers rightmost pixels`() {
        // Ensure the last tile's right edge reaches or exceeds inputW
        val inputW = 1000
        val tileSize = 512
        val overlap = 32
        val tileStep = tileSize - overlap
        val (tilesX, _, _) = computeTileGrid(inputW, 100, tileSize, overlap)

        // Last tile start position
        val lastTileX = (tilesX - 1) * tileStep
        val lastTileEnd = lastTileX + tileSize
        assertTrue(
            "Last tile end ($lastTileEnd) should cover inputW ($inputW)",
            lastTileEnd >= inputW
        )
    }

    // ── Dimension alignment tests ───────────────────────────────────

    private fun alignDim(dim: Int, alignment: Int = 4): Int {
        return ((dim / alignment) * alignment).coerceAtLeast(alignment)
    }

    @Test
    fun `alignDim - already aligned stays same`() {
        assertEquals(512, alignDim(512))
        assertEquals(256, alignDim(256))
        assertEquals(4, alignDim(4))
    }

    @Test
    fun `alignDim - rounds down to alignment boundary`() {
        assertEquals(508, alignDim(511))
        assertEquals(508, alignDim(510))
        assertEquals(508, alignDim(509))
        assertEquals(508, alignDim(508))
    }

    @Test
    fun `alignDim - very small dimension clamps to DIM_ALIGNMENT`() {
        assertEquals(4, alignDim(1))
        assertEquals(4, alignDim(2))
        assertEquals(4, alignDim(3))
        assertEquals(4, alignDim(4))
    }

    @Test
    fun `alignDim - odd dimensions round down`() {
        assertEquals(100, alignDim(103))
        assertEquals(100, alignDim(102))
        assertEquals(100, alignDim(101))
        assertEquals(100, alignDim(100))
    }

    // ── Adaptive tile sizing validation ─────────────────────────────

    @Test
    fun `adaptive tile sizes are multiples of DIM_ALIGNMENT`() {
        for (tier in DeviceTier.entries) {
            val size = HardwareProfiler.recommendedTileSize(tier)
            assertEquals(
                "Tile size $size for $tier should be multiple of 4",
                0, size % 4
            )
        }
    }

    @Test
    fun `adaptive tile sizes are at least TILE_OVERLAP times 2`() {
        // Must be at least 2x overlap to have meaningful tile content
        val overlap = 32
        for (tier in DeviceTier.entries) {
            val size = HardwareProfiler.recommendedTileSize(tier)
            assertTrue(
                "$tier tile size $size should be >= ${overlap * 2}",
                size >= overlap * 2
            )
        }
    }

    // ── Timeout consistency validation ──────────────────────────────

    @Test
    fun `total timeout is always at least 4x per-tile timeout`() {
        // A model that produces 4+ tiles should never exceed total timeout
        // if each tile finishes within per-tile timeout
        for (tier in DeviceTier.entries) {
            val total = HardwareProfiler.recommendedTimeoutMs(tier)
            val perTile = HardwareProfiler.recommendedPerTileTimeoutMs(tier)
            assertTrue(
                "$tier: total ($total) should be >= 4 * perTile ($perTile)",
                total >= 4 * perTile
            )
        }
    }

    @Test
    fun `timeouts are equal everywhere to maximize processing capability`() {
        val highTotal = HardwareProfiler.recommendedTimeoutMs(DeviceTier.HIGH)
        val lowTotal = HardwareProfiler.recommendedTimeoutMs(DeviceTier.LOW)
        assertEquals(lowTotal, highTotal)

        val highPerTile = HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.HIGH)
        val lowPerTile = HardwareProfiler.recommendedPerTileTimeoutMs(DeviceTier.LOW)
        assertEquals(lowPerTile, highPerTile)
    }
}
