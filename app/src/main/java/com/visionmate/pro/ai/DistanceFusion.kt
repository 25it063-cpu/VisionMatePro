package com.visionmate.pro.ai

import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.SensorData

object DistanceFusion {

    /**
     * Fuses visual object detections with ultrasonic range readings.
     * Note: Upper obstacles (TREE_BRANCH, etc.) are ONLY detected via camera
     * and MUST NOT be assigned fabricated ultrasonic distance values.
     */
    fun fuseDistance(obstacle: Obstacle, sensorData: SensorData): Obstacle {
        // Upper obstacles do NOT have dedicated ultrasonic distance sensors
        if (obstacle.isUpperObstacle || obstacle.objectType == ObjectType.TREE_BRANCH) {
            return obstacle.copy(measuredDistanceCm = null)
        }

        // Match distance based on direction
        val matchedDistance = when (obstacle.direction) {
            Direction.LEFT -> sensorData.leftDistanceCm
            Direction.RIGHT -> sensorData.rightDistanceCm
            Direction.CENTER -> sensorData.frontDistanceCm
        }

        return obstacle.copy(measuredDistanceCm = matchedDistance)
    }
}
