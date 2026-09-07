package com.visionmate.pro.communication

import com.visionmate.pro.model.SensorData
import android.util.Log
import kotlin.math.roundToInt

object DataParser {

    private var accumulatedData = SensorData()

    fun reset() {
        accumulatedData = SensorData()
    }

    /**
     * Parses sensor telemetry from ESP32.
     * Primary format:
     * SENSOR|SEQ=123|F=82.4|L=150.2|R=24.1|W=0|WV=820|FC=0|LC=0|RC=1
     *
     * Interpretations:
     * - F, L, R: Ultrasonic distances in cm (-1 means invalid/no echo)
     * - W: Water detected (1/0)
     * - WV: Raw water sensor value
     * - FC, LC, RC: Critical obstacle flags (1/0)
     * - SEQ: Packet sequence number
     *
     * Legacy formats are also supported as fallback.
     */
    fun parseSensorString(raw: String): SensorData {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return accumulatedData

        var front = accumulatedData.frontDistanceCm
        var left = accumulatedData.leftDistanceCm
        var right = accumulatedData.rightDistanceCm
        var water = accumulatedData.isWaterDetected
        var rawWater = accumulatedData.rawWaterValue
        var frontCrit = accumulatedData.isFrontCritical
        var leftCrit = accumulatedData.isLeftCritical
        var rightCrit = accumulatedData.isRightCritical
        var seq = accumulatedData.sequenceNumber
        var battery = accumulatedData.caneBatteryPercent
        var sos = accumulatedData.isPhysicalSosPressed

        try {
            // 0. Explicit physical SOS signal from ESP32 (e.g. Serial.println("SOS_TRIGGERED");)
            if (trimmed.equals("SOS_TRIGGERED", ignoreCase = true) ||
                trimmed.equals("SOS_PRESSED", ignoreCase = true) ||
                trimmed.contains("SOS_TRIGGERED", ignoreCase = true)) {
                sos = true
            } else if (trimmed.startsWith("SENSOR|", ignoreCase = true) || trimmed.contains("|")) {
                val tokens = trimmed.split("|")
                for (token in tokens) {
                    val kv = token.split("=")
                    if (kv.size == 2) {
                        val key = kv[0].trim().uppercase()
                        val valueStr = kv[1].trim()

                        when (key) {
                            "F", "FRONT" -> {
                                val floatVal = valueStr.toFloatOrNull()
                                if (floatVal != null) {
                                    front = if (floatVal < 0) -1 else floatVal.roundToInt()
                                }
                            }
                            "L", "LEFT" -> {
                                val floatVal = valueStr.toFloatOrNull()
                                if (floatVal != null) {
                                    left = if (floatVal < 0) -1 else floatVal.roundToInt()
                                }
                            }
                            "R", "RIGHT" -> {
                                val floatVal = valueStr.toFloatOrNull()
                                if (floatVal != null) {
                                    right = if (floatVal < 0) -1 else floatVal.roundToInt()
                                }
                            }
                            "W", "WATER" -> {
                                water = (valueStr == "1" || valueStr.equals("true", ignoreCase = true))
                            }
                            "WV" -> {
                                rawWater = valueStr.toIntOrNull() ?: rawWater
                            }
                            "FC" -> {
                                frontCrit = (valueStr == "1" || valueStr.equals("true", ignoreCase = true))
                            }
                            "LC" -> {
                                leftCrit = (valueStr == "1" || valueStr.equals("true", ignoreCase = true))
                            }
                            "RC" -> {
                                rightCrit = (valueStr == "1" || valueStr.equals("true", ignoreCase = true))
                            }
                            "SEQ" -> {
                                seq = valueStr.toLongOrNull() ?: seq
                            }
                            "B", "BAT", "BATTERY" -> {
                                battery = valueStr.toIntOrNull() ?: battery
                            }
                            "S", "SOS" -> {
                                sos = (valueStr == "1" || valueStr.equals("true", ignoreCase = true))
                            }
                        }
                    }
                }
            } else {
                // 2. Legacy fallback parser
                val cleaned = trimmed.replace("[", "").replace("]", "").replace("{", "").replace("}", "")
                val tokens = cleaned.split(Regex("[,\\s;]+"))

                for (token in tokens) {
                    if (token.isBlank()) continue
                    val parts = token.split(":")
                    if (parts.size >= 2) {
                        val key = if (parts.size >= 3 && parts[0].trim().uppercase() == "DIST") {
                            parts[1].trim().filter { it.isLetter() }.uppercase()
                        } else {
                            parts[0].trim().filter { it.isLetter() }.uppercase()
                        }
                        val valueStr = parts.last().trim()

                        when (key) {
                            "FRONT", "F" -> {
                                val parsed = valueStr.toDoubleOrNull()?.roundToInt() ?: valueStr.toIntOrNull()
                                if (parsed != null) front = parsed
                            }
                            "LEFT", "L" -> {
                                val parsed = valueStr.toDoubleOrNull()?.roundToInt() ?: valueStr.toIntOrNull()
                                if (parsed != null) left = parsed
                            }
                            "RIGHT", "R" -> {
                                val parsed = valueStr.toDoubleOrNull()?.roundToInt() ?: valueStr.toIntOrNull()
                                if (parsed != null) right = parsed
                            }
                            "WATER", "W" -> {
                                val numVal = valueStr.toIntOrNull()
                                water = if (numVal != null) (numVal > 500 || numVal == 1) else valueStr.equals("true", ignoreCase = true)
                            }
                            "BATTERY", "BAT", "B" -> {
                                battery = valueStr.toIntOrNull() ?: battery
                            }
                            "SOS", "S" -> {
                                sos = (valueStr == "1" || valueStr.equals("true", ignoreCase = true))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DataParser", "Error parsing: $raw, error: ${e.message}")
        }

        val resultData = SensorData(
            frontDistanceCm = front,
            leftDistanceCm = left,
            rightDistanceCm = right,
            isWaterDetected = water,
            rawWaterValue = rawWater,
            isFrontCritical = frontCrit,
            isLeftCritical = leftCrit,
            isRightCritical = rightCrit,
            sequenceNumber = seq,
            caneBatteryPercent = battery,
            isPhysicalSosPressed = sos,
            timestamp = System.currentTimeMillis()
        )

        // Store non-SOS state in accumulatedData so subsequent regular telemetry lines don't re-trigger SOS
        accumulatedData = if (sos) {
            resultData.copy(isPhysicalSosPressed = false)
        } else {
            resultData
        }

        return resultData
    }
}
