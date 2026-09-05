package com.visionmate.pro.intent

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.visionmate.pro.model.AppLanguage
import kotlinx.coroutines.tasks.await

class LanguageDetector {
    private val languageIdentifier = LanguageIdentification.getClient()

    companion object {
        // High-confidence anchors (Native + English + Transliterated) that trigger an immediate switch
        val TAMIL_ANCHORS = listOf("tamil", "thamizh", "tamizh", "தமிழ்", "தமிழில்", "தமீழ்", "vanakkam")
        val HINDI_ANCHORS = listOf("hindi", "hinde", "hindu", "हिन्दी", "हिंदी", "हिंदी में", "ஹிந்தி", "இந்தி", "హిందీ", "namaste", "rasta")
        val TELUGU_ANCHORS = listOf("telugu", "telagu", "తెలుగు", "తెలుగులో", "தெலுங்கு", "తేలుగు", "namaskaram")
        val MALAYALAM_ANCHORS = listOf("malayalam", "മലയാളം", "മലയാളത്തിൽ", "മലയാളം", "മലയാളം", "മലായാളം", "namaskaram")
        val ENGLISH_ANCHORS = listOf("english", "inglish", "angilam", "angrezi", "speak", "talk", "navigate")
    }

    suspend fun detectLanguage(candidates: List<String>, currentLanguage: AppLanguage): AppLanguage {
        if (candidates.isEmpty()) return currentLanguage

        // 1. Universal Anchor Matching (Highest Priority - Scanning all interpretations)
        // This ensures "Hindi" triggers a switch even if transcribed in Tamil script (e.g., "ஹிந்தி")
        val combinedText = candidates.joinToString(" ").lowercase()
        
        if (HINDI_ANCHORS.any { combinedText.contains(it) }) return AppLanguage.HINDI
        if (TAMIL_ANCHORS.any { combinedText.contains(it) }) return AppLanguage.TAMIL
        if (TELUGU_ANCHORS.any { combinedText.contains(it) }) return AppLanguage.TELUGU
        if (MALAYALAM_ANCHORS.any { combinedText.contains(it) }) return AppLanguage.MALAYALAM
        if (ENGLISH_ANCHORS.any { combinedText.contains(it) }) return AppLanguage.ENGLISH

        // 2. Script Detection (Priority for native characters)
        for (text in candidates) {
            if (text.any { it in '\u0900'..'\u097F' }) return AppLanguage.HINDI
            if (text.any { it in '\u0B80'..'\u0BFF' }) return AppLanguage.TAMIL
            if (text.any { it in '\u0C00'..'\u0C7F' }) return AppLanguage.TELUGU
            if (text.any { it in '\u0D00'..'\u0D7F' }) return AppLanguage.MALAYALAM
        }

        // 3. Probabilistic ML Kit Detection (Standard Fallback)
        return try {
            val possibleLanguages = languageIdentifier.identifyPossibleLanguages(candidates[0]).await()
            
            // If English confidence is reasonable, allow switching back
            val englishMatch = possibleLanguages.find { it.languageTag == "en" }
            if (englishMatch != null && englishMatch.confidence > 0.4f) return AppLanguage.ENGLISH

            val topResult = possibleLanguages.firstOrNull()
            if (topResult != null && topResult.confidence > 0.3f) {
                when (topResult.languageTag) {
                    "hi" -> AppLanguage.HINDI
                    "ta" -> AppLanguage.TAMIL
                    "te" -> AppLanguage.TELUGU
                    "ml" -> AppLanguage.MALAYALAM
                    else -> currentLanguage
                }
            } else {
                currentLanguage
            }
        } catch (e: Exception) {
            currentLanguage
        }
    }
}
