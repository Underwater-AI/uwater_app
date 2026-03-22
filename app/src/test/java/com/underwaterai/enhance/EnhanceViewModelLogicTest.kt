package com.underwaterai.enhance

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class EnhanceViewModelLogicTest {

    private fun checkMightNotBeMarine(classifications: List<String>): Boolean {
        val nonMarineClasses = listOf("suit", "sunglasses", "neck brace", "face", "person", "car", "dog", "cat", "mask", "oxygen mask", "bikini", "racket")
        val marineClasses = listOf("fish", "scuba diver", "coral reef", "anemone", "shark", "whale", "ray", "submarine", "turtle", "crab", "jellyfish", "sea")
        
        return classifications.any { c -> nonMarineClasses.any { it in c.lowercase() } } && 
               !classifications.any { c -> marineClasses.any { it in c.lowercase() } }
    }

    @Test
    fun testUnderwaterImage() {
        val classifications = listOf("scuba diver", "ocean", "water")
        assertFalse(checkMightNotBeMarine(classifications))
    }

    @Test
    fun testNonUnderwaterImage() {
        val classifications = listOf("person in a suit", "city", "car")
        assertTrue(checkMightNotBeMarine(classifications))
    }

    @Test
    fun testAmbiguousImage() {
        val classifications = listOf("person", "scuba diver")
        assertFalse("Should be allowed if both non-marine and marine present", checkMightNotBeMarine(classifications))
    }

    @Test
    fun testDogImage() {
        val classifications = listOf("black dog", "poodle")
        assertTrue(checkMightNotBeMarine(classifications))
    }
}
