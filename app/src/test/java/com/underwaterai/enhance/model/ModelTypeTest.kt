package com.underwaterai.enhance.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [ModelType] enum configuration.
 *
 * These validate invariants that, if violated, would cause crashes or
 * silent image quality issues at runtime. They act as a safety net
 * whenever models are added or reconfigured.
 */
class ModelTypeTest {

    @Test
    fun `all 9 models are defined`() {
        assertEquals(9, ModelType.entries.size)
    }

    @Test
    fun `all fileNames end with dot pt`() {
        for (model in ModelType.entries) {
            assertTrue(
                "${model.name}: fileName '${model.fileName}' must end with .pt",
                model.fileName.endsWith(".pt")
            )
        }
    }

    @Test
    fun `no duplicate fileNames`() {
        val names = ModelType.entries.map { it.fileName }
        assertEquals(
            "Duplicate fileNames detected: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}",
            names.size, names.distinct().size
        )
    }

    @Test
    fun `no duplicate displayNames`() {
        val names = ModelType.entries.map { it.displayName }
        assertEquals(
            "Duplicate displayNames detected: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}",
            names.size, names.distinct().size
        )
    }

    @Test
    fun `all scale factors are 4`() {
        for (model in ModelType.entries) {
            assertEquals("${model.name} scaleFactor", 4, model.scaleFactor)
        }
    }

    @Test
    fun `maxInputSide is positive and reasonable`() {
        for (model in ModelType.entries) {
            assertTrue(
                "${model.name}: maxInputSide (${model.maxInputSide}) must be > 0",
                model.maxInputSide > 0
            )
            assertTrue(
                "${model.name}: maxInputSide (${model.maxInputSide}) must be <= 2048",
                model.maxInputSide <= 2048
            )
        }
    }

    @Test
    fun `lightweight models (1-5) have maxInputSide 640`() {
        val lightweight = listOf(
            ModelType.MODEL_1, ModelType.MODEL_2, ModelType.MODEL_3,
            ModelType.MODEL_4, ModelType.MODEL_5
        )
        for (model in lightweight) {
            assertEquals("${model.name} maxInputSide", 640, model.maxInputSide)
        }
    }

    @Test
    fun `ESRGAN models (6-9) have maxInputSide 480`() {
        val esrgan = listOf(
            ModelType.MODEL_6, ModelType.MODEL_7,
            ModelType.MODEL_8, ModelType.MODEL_9
        )
        for (model in esrgan) {
            assertEquals("${model.name} maxInputSide", 480, model.maxInputSide)
        }
    }

    @Test
    fun `ESRGAN models have ESRGAN in description`() {
        val esrgan = listOf(
            ModelType.MODEL_6, ModelType.MODEL_7,
            ModelType.MODEL_8, ModelType.MODEL_9
        )
        for (model in esrgan) {
            assertTrue(
                "${model.name}: description should mention ESRGAN",
                model.description.contains("ESRGAN", ignoreCase = true)
            )
        }
    }

    @Test
    fun `all models have non-blank display fields`() {
        for (model in ModelType.entries) {
            assertTrue("${model.name}: displayName blank", model.displayName.isNotBlank())
            assertTrue("${model.name}: description blank", model.description.isNotBlank())
            assertTrue("${model.name}: bestFor blank", model.bestFor.isNotBlank())
        }
    }

    @Test
    fun `maxInputSide is divisible by DIM_ALIGNMENT (4)`() {
        for (model in ModelType.entries) {
            assertEquals(
                "${model.name}: maxInputSide ${model.maxInputSide} must be divisible by 4",
                0, model.maxInputSide % 4
            )
        }
    }

    @Test
    fun `output resolution with scaleFactor is reasonable`() {
        for (model in ModelType.entries) {
            val maxOutput = model.maxInputSide * model.scaleFactor
            assertTrue(
                "${model.name}: max output side ${maxOutput} should be <= 4096",
                maxOutput <= 4096
            )
            assertTrue(
                "${model.name}: max output side ${maxOutput} should be >= 256",
                maxOutput >= 256
            )
        }
    }
}
