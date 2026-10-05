package com.example.data.util

data class ExtractedFact(
    val fact: String,
    val category: String
)

object MemoryExtractor {

    /**
     * Extracts persistent, token-minimal facts about the user from their chat prompts.
     * Keeps facts ultra-short so minimal tokens are used in prompt context.
     * Examples:
     * - "mai 17 ka hu" / "I am 17" -> "Sasuke is 17 years old"
     * - "class 11th math ka chapter 2" -> "Sasuke is in 11th grade (Math)"
     * - "mera naam Sasuke hai" -> "User's name is Sasuke"
     */
    fun extractFacts(text: String): List<ExtractedFact> {
        val lower = text.lowercase().trim()
        val facts = mutableListOf<ExtractedFact>()

        // 1. Age Detection: e.g. "mai 17 ka hu", "i am 17", "17 years old", "age 17"
        val ageRegex = Regex("""(?:\b(?:mai|me|i am|i'm|age|umar|i am of)\s+(\d{1,2})\s*(?:ka\s*hu|saal|years?|yo|\b))|(?:\b(\d{1,2})\s*(?:years?\s*old|saal\s*ka))""", RegexOption.IGNORE_CASE)
        val ageMatch = ageRegex.find(lower)
        if (ageMatch != null) {
            val age = ageMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            if (age != null) {
                val ageInt = age.toIntOrNull()
                if (ageInt != null && ageInt in 5..99) {
                    facts.add(ExtractedFact("Sasuke is $age years old", "Personal"))
                }
            }
        }

        // 2. Class / Grade / Education: e.g. "class 11th", "11th class", "11th me hu", "in grade 11", "std 11"
        val gradeRegex = Regex("""(?:\b(?:class|grade|std|standard)\s*(\d{1,2}(?:st|nd|rd|th)?))|(?:\b(\d{1,2}(?:st|nd|rd|th)?)\s*(?:class|grade|me\s*hu|standard))""", RegexOption.IGNORE_CASE)
        val gradeMatch = gradeRegex.find(lower)
        if (gradeMatch != null) {
            val grade = gradeMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            if (grade != null) {
                val subject = when {
                    lower.contains("math") || lower.contains("ganit") -> "Math"
                    lower.contains("physics") -> "Physics"
                    lower.contains("chemistry") -> "Chemistry"
                    lower.contains("biology") -> "Biology"
                    lower.contains("science") -> "Science"
                    lower.contains("commerce") -> "Commerce"
                    lower.contains("cs") || lower.contains("computer") -> "Computer Science"
                    else -> null
                }
                val factText = if (subject != null) {
                    "Sasuke is in $grade grade studying $subject"
                } else {
                    "Sasuke is in $grade grade"
                }
                facts.add(ExtractedFact(factText, "Education"))
            }
        }

        // 3. Name Detection: e.g. "mera naam X hai", "my name is X", "call me X"
        val nameRegex = Regex("""(?:\b(?:mera\s*naam|my\s*name\s*is|call\s*me)\s+([A-Za-z0-9_-]{2,20}))""", RegexOption.IGNORE_CASE)
        val nameMatch = nameRegex.find(lower)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1]
            if (!listOf("a", "the", "an", "here", "what", "is").contains(name.lowercase())) {
                facts.add(ExtractedFact("User's name is ${name.replaceFirstChar { it.uppercase() }}", "Personal"))
            }
        }

        // 4. College / School / Degree:
        if (lower.contains("btech") || lower.contains("b.tech")) {
            facts.add(ExtractedFact("Pursuing B.Tech degree", "Education"))
        } else if (lower.contains("jee") || lower.contains("neet")) {
            facts.add(ExtractedFact("Preparing for competitive exams (JEE/NEET)", "Education"))
        }

        // 5. Language / Regional preferences:
        if (lower.contains("hinglish me baat") || lower.contains("hinglish me bolo") || lower.contains("explain in hindi")) {
            facts.add(ExtractedFact("Prefers answers in Hinglish / Hindi", "Preferences"))
        }

        return facts
    }
}
