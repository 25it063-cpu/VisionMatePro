package com.visionmate.pro.voice

import com.visionmate.pro.model.CommandIntent
import com.visionmate.pro.model.VoiceCommand

object IntentClassifier {

    fun classify(rawInput: String): VoiceCommand {
        val cleanRaw = rawInput.lowercase().trim()
        val text = cleanRaw.replace(Regex("[^a-z0-9\\s\u0B80-\u0BFF\u0900-\u097F]"), "")
        val compressed = text.replace(" ", "")

        val digits = text.filter { it.isDigit() }
        
        // Navigation Keywords
        val isNavigate = text.contains("navigate") || text.contains("take me to") || 
                        text.contains("direction") || text.contains("route") ||
                        text.contains("வழி") || text.contains("रास्ता") || text.contains("दिखाओ")

        return when {
            // Navigation Priority
            isNavigate -> {
                val destination = extractDestination(text)
                VoiceCommand(
                    rawText = rawInput,
                    intent = CommandIntent.NAVIGATE,
                    parameters = if (destination.isNotEmpty()) mapOf("destination" to destination) else emptyMap()
                )
            }

            // Change SOS Number
            (compressed.contains("sos") || text.contains("emergency")) && 
            (text.contains("number") || text.contains("contact") || text.contains("phone")) && 
            (text.contains("change") || text.contains("set") || text.contains("update")) -> {
                val extractedNum = if (digits.length >= 3) digits else extractNumbersFromWords(text)
                VoiceCommand(
                    rawText = rawInput,
                    intent = CommandIntent.CHANGE_EMERGENCY_CONTACT,
                    parameters = mapOf("number" to extractedNum)
                )
            }

            // Cancel SOS
            compressed.contains("cancel") || compressed.contains("stop") || text.contains("ரத்து") || text.contains("रोक") -> 
                VoiceCommand(rawInput, CommandIntent.CANCEL_SOS)

            // Direct SOS Trigger
            compressed.contains("sos") || text.contains("emergency") || text.contains("help") -> 
                VoiceCommand(rawInput, CommandIntent.EMERGENCY)

            else -> VoiceCommand(rawInput, CommandIntent.UNKNOWN)
        }
    }

    private fun extractDestination(text: String): String {
        val keywords = listOf(
            "navigate to", "take me to", "direction to", "route to", 
            "வழி காட்டு", "रास्ता दिखाओ", "navigate", "directions"
        )
        var result = text
        for (keyword in keywords) {
            if (result.contains(keyword)) {
                result = result.substringAfter(keyword).trim()
                break
            }
        }
        // Remove "the" if it's the start (e.g. "navigate to the hospital" -> "hospital")
        return result.removePrefix("the ").trim()
    }

    private fun extractNumbersFromWords(input: String): String {
        val wordToDigit = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9"
        )
        val words = input.split(" ")
        val result = StringBuilder()
        for (word in words) {
            wordToDigit[word]?.let { result.append(it) }
        }
        return result.toString()
    }
}
