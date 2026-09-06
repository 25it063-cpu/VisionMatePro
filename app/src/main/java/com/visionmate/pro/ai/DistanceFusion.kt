package com.visionmate.pro.ai

import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.SensorData

object DistanceFusion {

    /**
     * Retrieves the physical distance (cm) for a specific ultrasonic sensor sector.
     * Returns null if reading is negative (-1) or invalid.
     */
    fun getSectorDistance(sector: Direction, sensorData: SensorData): Int? {
        val dist = when (sector) {
            Direction.LEFT -> sensorData.leftDistanceCm
            Direction.CENTER -> sensorData.frontDistanceCm
            Direction.RIGHT -> sensorData.rightDistanceCm
        }
        return if (dist >= 0) dist else null
    }

    /**
     * Determines whether the matching ultrasonic sector has a critical flag or reading.
     */
    fun isSectorCritical(sector: Direction, sensorData: SensorData): Boolean {
        return when (sector) {
            Direction.LEFT -> sensorData.isLeftCritical || sensorData.leftDistanceCm in 0..30
            Direction.CENTER -> sensorData.isFrontCritical || sensorData.frontDistanceCm in 0..30
            Direction.RIGHT -> sensorData.isRightCritical || sensorData.rightDistanceCm in 0..30
        }
    }

    /**
     * Fuses visual object detections with their matching ultrasonic sensor sector.
     *
     * Rules:
     * - YOLO identifies WHAT the object is (objectType).
     * - Bounding-box center determines ONLY the eligible ultrasonic sector (LEFT, CENTER, RIGHT).
     * - Physical direction and distance come EXCLUSIVELY from that matching ultrasonic sensor:
     *     - Matching sensor distance >= 0: direction = sector, measuredDistanceCm = distance.
     *     - Matching sensor distance == -1: measuredDistanceCm = null, direction = Direction.CENTER.
     * - Never borrows another sector's distance when the matching sensor is invalid (-1).
     * - Upper obstacles (TREE_BRANCH, etc.) have no ultrasonic sensors: measuredDistanceCm = null.
     */
    fun fuseDistance(obstacle: Obstacle, sensorData: SensorData): Obstacle {
        // Upper obstacles (e.g. overhead tree branches) do not have ultrasonic range sensors
        if (obstacle.isUpperObstacle || obstacle.objectType == ObjectType.TREE_BRANCH) {
            return obstacle.copy(
                direction = Direction.CENTER,
                measuredDistanceCm = null
            )
        }

        // Determine eligible sector using the existing DirectionDetector
        val eligibleSector = DirectionDetector.determineDirection(obstacle.boundingBox)
        val sectorDist = getSectorDistance(eligibleSector, sensorData)

        return if (sectorDist != null) {
            obstacle.copy(
                direction = eligibleSector,
                measuredDistanceCm = sectorDist
            )
        } else {
            // Matched sensor is invalid (-1): do not borrow another sensor's distance
            obstacle.copy(
                direction = Direction.CENTER,
                measuredDistanceCm = null
            )
        }
    }
}


