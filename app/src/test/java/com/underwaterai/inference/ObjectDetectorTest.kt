package com.underwaterai.inference

import org.junit.Test
import org.junit.Assert.*
import com.underwaterai.enhance.model.DetectionResult

class ObjectDetectorTest {

    @Test
    fun testDetectionDataClass() {
        val detection = DetectionResult(
            box = floatArrayOf(10f, 10f, 50f, 50f),
            score = 0.95f,
            labelIndex = 1,
            labelName = "Fish"
        )

        assertEquals(10f, detection.box[0])
        assertEquals(0.95f, detection.score)
        assertEquals(1, detection.labelIndex)
        assertEquals("Fish", detection.labelName)
    }
}
