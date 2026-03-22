package com.underwaterai.enhance

import org.junit.Assert.*
import org.junit.Test

class MarineHeuristicsTest {

    // Simple Under-water heuristic check based on common ImageNet/COCO classes
    private val nonMarineClasses = listOf("suit", "sunglasses", "neck brace", "face", "person", "car", "dog", "cat", "mask", "oxygen mask", "bikini", "racket", "building", "tree", "street", "bicycle", "laptop", "phone", "furniture", "clothing")
    private val marineClasses = listOf("fish", "scuba diver", "coral reef", "anemone", "shark", "whale", "ray", "submarine", "turtle", "crab", "jellyfish", "sea", "water", "ocean", "diver", "reef", "marine", "kelp")

    fun isEcosystemMismatch(labels: List<String>): Boolean {
        val mightNotBeMarine = labels.any { c -> nonMarineClasses.any { it in c.lowercase() } } &&
                !labels.any { c -> marineClasses.any { it in c.lowercase() } }
        return mightNotBeMarine
    }

    @Test
    fun testMarineInference() {
        // A diver image
        val diverLabels = listOf("scuba diver", "ocean", "person")
        assertFalse("Should be marine", isEcosystemMismatch(diverLabels))
        
        // A random city photo
        val cityLabels = listOf("car", "building", "street")
        assertTrue("Should be mismatch", isEcosystemMismatch(cityLabels))
        
        // An empty inference (ambiguous)
        val emptyLabels = emptyList<String>()
        assertFalse("Empty should not trigger mismatch by default", isEcosystemMismatch(emptyLabels))
    }
}
