package com.visionmate.pro.intent

import com.visionmate.pro.model.CommandIntent
import com.visionmate.pro.model.VoiceCommand

/**
 * Enhanced IntentClassifier with native script support for all 5 languages.
 * Acts as a "High-Speed Bypass" for explicit commands.
 */
object IntentClassifier {
    fun classify(rawInput: String): VoiceCommand {
        val cleanRaw = rawInput.lowercase().trim()
        val text = cleanRaw.replace(Regex("[^a-z0-9\\s\\u0B80-\\u0BFF\\u0900-\\u097F\\u0C00-\\u0C7F\\u0D00-\\u0D7F]"), "")

        // 1. Language Switch Override (Native Script Triggers)
        val langTriggers = listOf(
            "language", "speak", "talk", "pesu", "sollu", "bolo", "boliye", "matladu", "parayu",
            "மொழி", "பேசு", "சொல்லு", // Tamil
            "भाषा", "बोलो", "बोलिए", // Hindi
            "భాష", "మాట్లాడు", // Telugu
            "ഭാഷ", "പറയൂ" // Malayalam
        )
        
        if (langTriggers.any { text.contains(it) } || isOnlyLanguageName(text)) {
            val target = when {
                text.contains("hindi") || text.contains("हिन्दी") || text.contains("ஹிந்தி") || text.contains("హిందీ") || text.contains("ഹിന്ദി") -> "hi"
                text.contains("tamil") || text.contains("தமிழ்") || text.contains("तमिल") || text.contains("తమిళం") || text.contains("തമിഴ്") -> "ta"
                text.contains("telugu") || text.contains("తెలుగు") || text.contains("தெலுங்கு") || text.contains("तेलुगु") -> "te"
                text.contains("malayalam") || text.contains("മലയാളம்") || text.contains("மலையாளம்") -> "ml"
                text.contains("english") || text.contains("ஆங்கிலம்") || text.contains("अंग्रेज़ी") || text.contains("angilam") -> "en"
                else -> null
            }
            if (target != null) return VoiceCommand(rawInput, CommandIntent.CHANGE_LANGUAGE, mapOf("language" to target))
        }

        // 2. Immediate Navigation Detection
        val navTriggers = listOf("navigate", "direction", "route", "way", "வழி", "rasta", "रास्ता", "దారి", "daree", "വഴി", "vazhi")
        if (navTriggers.any { text.contains(it) }) {
            val destination = extractParameter(text, navTriggers + listOf("to", "take me", "show", "me", "காட்டு", "dikhao", "చూపండి", "കാണിക്കూ", "the"))
            if (destination.isNotEmpty()) {
                return VoiceCommand(rawInput, CommandIntent.NAVIGATE, mapOf("destination" to destination))
            }
        }

        // 3. SOS and Emergency
        if (text.contains("sos") || text.contains("emergency") || text.contains("help") || text.contains("உதவி") || text.contains("madad")) {
            return VoiceCommand(rawInput, CommandIntent.EMERGENCY)
        }

        // 4. Greetings
        if (text.contains("vanakkam") || text.contains("namaste") || text.contains("hello") || text.contains("hi") || text.contains("namaskaram")) {
            return VoiceCommand(rawInput, CommandIntent.GREETING)
        }

        return VoiceCommand(rawInput, CommandIntent.UNKNOWN)
    }

    private fun isOnlyLanguageName(text: String): Boolean {
        val langs = listOf("tamil", "hindi", "telugu", "malayalam", "english", "தமிழ்", "हिन्दी", "தெలుగు", "മലയാളம்", "ஹிந்தி", "இந்தி")
        return langs.any { it == text }
    }

    private fun extractParameter(text: String, stopWords: List<String>): String {
        var cleaned = text
        for (word in stopWords) {
            cleaned = cleaned.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), "")
        }
        return cleaned.trim()
    }
}
