package com.visionmate.pro.communication

import com.visionmate.pro.model.SensorData

object DataParser {

    /**
     * Parses key-value sensor string from ESP32 telemetry.
     * Example input: "FRONT:100,LEFT:180,RIGHT:75,WATER:0,BATTERY:87,SOS:0"
     */
    fun parseSensorString(raw: String): SensorData {
        var front = 180
        var left = 200
        var right = 200
        var water = false
        var battery = 87
        var sos = false

        try {
            val pairs = raw.trim().split(",")
            for (pair in pairs) {
                val kv = pair.split(":")
                if (kv.size == 2) {
                    val key = kv[0].trim().uppercase()
                    val value = kv[1].trim()
                    when (key) {
                        "FRONT" -> front = value.toIntOrNull() ?: front
                        "LEFT" -> left = value.toIntOrNull() ?: left
                        "RIGHT" -> right = value.toIntOrNull() ?: right
                        "WATER" -> water = (value == "1" || value.equals("true", ignoreCase = true))
                        "BATTERY" -> battery = value.toIntOrNull() ?: battery
                        "SOS" -> sos = (value == "1" || value.equals("true", ignoreCase = true))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
