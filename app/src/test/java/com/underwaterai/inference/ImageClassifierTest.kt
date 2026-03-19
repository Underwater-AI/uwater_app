package com.underwaterai.inference

import org.junit.Test
import org.junit.Assert.*
import com.underwaterai.enhance.model.ImageClassifier

class ImageClassifierTest {

    @Test
    fun testPredictionDataClass() {
        val prediction = ImageClassifier.Prediction(
            label = "Lionfish",
            score = 0.88f,
            isInvasive = true
        )

        assertEquals("Lionfish", prediction.label)
        assertEquals(0.88f, prediction.score)
        assertTrue(prediction.isInvasive)
    }
}
