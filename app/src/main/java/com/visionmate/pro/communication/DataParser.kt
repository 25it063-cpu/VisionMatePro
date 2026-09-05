package com.visionmate.pro.communication

import com.visionmate.pro.model.SensorData
import android.util.Log

object DataParser {

    /**
     * Advanced parser that handles commas, spaces, or tabs as delimiters.
     * Example: "F:10, L:20" or "F:10 L:20" or "[FRONT:100]"
     */
    fun parseSensorString(raw: String): SensorData {
        var front = 180
        var left = 200
        var right = 200
        var water = false
        var battery = 87
        var sos = false

        try {
            // 1. Clean the string and handle multiple possible delimiters (comma, space, semicolon)
            val cleaned = raw.replace("[", "").replace("]", "").replace("{", "").replace("}", "")
            val tokens = cleaned.split(Regex("[,\\s;]+"))
            
            for (token in tokens) {
                if (token.contains(":")) {
                    val kv = token.split(":")
                    if (kv.size == 2) {
                        // Strip non-letters from key (e.g., " FRONT" or "(FRONT")
                        val key = kv[0].trim().filter { it.isLetter() }.uppercase()
                        val value = kv[1].trim()
                        
                        when (key) {
                            "FRONT", "F" -> front = value.toIntOrNull() ?: front
                            "LEFT", "L" -> left = value.toIntOrNull() ?: left
                            "RIGHT", "R" -> right = value.toIntOrNull() ?: right
                            "WATER", "W" -> water = (value == "1" || value.equals("true", ignoreCase = true))
                            "BATTERY", "B" -> battery = value.toIntOrNull() ?: battery
                            "SOS", "S" -> sos = (value == "1" || value.equals("true", ignoreCase = true))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DataParser", "Error parsing: $raw")
        }

        return SensorData(
            frontDistanceCm = front,
            leftDistanceCm = left,
            rightDistanceCm = right,
            isWaterDetected = water,
            caneBatteryPercent = battery,
            isPhysicalSosPressed = sos,
            timestamp = System.currentTimeMillis()
        )
    }
}
