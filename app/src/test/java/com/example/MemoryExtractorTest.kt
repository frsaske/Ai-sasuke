package com.example

import com.example.data.util.MemoryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractorTest {

    @Test
    fun extractFacts_detectsAgeInHindiAndEnglish() {
        val hindiResult = MemoryExtractor.extractFacts("Mai 17 ka hu bhai")
        assertTrue(hindiResult.any { it.fact.contains("17 years old") })

        val englishResult = MemoryExtractor.extractFacts("I am 17 years old")
        assertTrue(englishResult.any { it.fact.contains("17 years old") })
    }

    @Test
    fun extractFacts_detectsGradeAndMathSubject() {
        val result = MemoryExtractor.extractFacts("I need help mene kuch nhi pdha.. Class 11th math ka chatpter 2 explain kr")
        assertTrue(result.any { it.fact.contains("11th grade") && it.fact.contains("Math") })
    }

    @Test
    fun extractFacts_returnsEmptyForGenericChat() {
        val result = MemoryExtractor.extractFacts("What is the capital of France?")
        assertTrue(result.isEmpty())
    }
}
