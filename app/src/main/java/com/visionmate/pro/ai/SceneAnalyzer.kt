package com.visionmate.pro.ai

import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.SensorData

class SceneAnalyzer {

    fun summarizeScene(obstacles: List<Obstacle>, sensorData: SensorData): String {
        if (obstacles.isEmpty() && sensorData.frontDistanceCm > 150) {
            return "Path is clear ahead."
        }

        val descriptions = obstacles.map { obs ->
            val dirStr = when (obs.direction) {
                com.visionmate.pro.model.Direction.LEFT -> "on your left"
                com.visionmate.pro.model.Direction.RIGHT -> "on your right"
                com.visionmate.pro.model.Direction.CENTER -> "ahead"
            }

            val typeStr = obs.objectType.name.lowercase().replace("_", " ")

            if (obs.measuredDistanceCm != null) {
                val meters = obs.measuredDistanceCm / 100.0
                if (meters >= 1.0) {
                    "$typeStr $dirStr at ${"%.1f".format(meters)} meters"
                } else {
                    "$typeStr $dirStr at ${obs.measuredDistanceCm} centimeters"
                }
            } else {
                "low $typeStr $dirStr"
            }
        }

        return "I see " + descriptions.joinToString(", ") + "."
    }
}
