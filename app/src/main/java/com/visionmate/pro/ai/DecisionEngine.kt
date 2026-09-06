package com.visionmate.pro.ai

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.BoundingBox
import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.ObstacleAlert
import com.visionmate.pro.model.ProximityState
import com.visionmate.pro.model.SensorData
import com.visionmate.pro.model.SystemHealth
import com.visionmate.pro.safety.AlertCooldownManager
import com.visionmate.pro.safety.ProximityStateManager
import com.visionmate.pro.language.ResponseTemplateManager

class DecisionEngine(
    private val proximityStateManager: ProximityStateManager,
    private val alertCooldownManager: AlertCooldownManager,
    private val objectTracker: ObjectTracker,
    private val responseTemplateManager: ResponseTemplateManager
) {

    /**
     * Camera / YOLO Pipeline:
     * - ONLY this method calls objectTracker.updateTracks().
     * - Fuses visual objects with their matching sector ultrasonic distance.
     * - Evaluates non-occupied standalone ultrasonic hazards and water hazards.
     */
    fun processFrameAndSensors(
        rawDetections: List<Obstacle>,
        sensorData: SensorData,
        systemHealth: SystemHealth,
        language: AppLanguage
    ): ObstacleAlert? {
        val candidatesToEvaluate = mutableListOf<Obstacle>()
        val matchedSectors = mutableSetOf<Direction>()

        // 1. Visual detections fused with their corresponding sector ultrasonic distance & direction
        if (rawDetections.isNotEmpty()) {
            val trackedDetections = objectTracker.updateTracks(rawDetections)
            for (obs in trackedDetections) {
                if (!obs.isUpperObstacle && obs.objectType != ObjectType.TREE_BRANCH) {
                    val sector = DirectionDetector.determineDirection(obs.boundingBox)
                    matchedSectors.add(sector)
                }
                candidatesToEvaluate.add(DistanceFusion.fuseDistance(obs, sensorData))
            }
        }

        // 2. Unmatched Ultrasonic sensor hazards (direct hardware readings)
        val sectorsToCheck = listOf(
            Triple(Direction.CENTER, sensorData.frontDistanceCm, sensorData.isFrontCritical),
            Triple(Direction.LEFT, sensorData.leftDistanceCm, sensorData.isLeftCritical),
            Triple(Direction.RIGHT, sensorData.rightDistanceCm, sensorData.isRightCritical)
        )

        for ((sector, distance, isCrit) in sectorsToCheck) {
            if (distance >= 0 && !matchedSectors.contains(sector)) {
                val proxState = proximityStateManager.calculateProximityState(distance)
                if (proxState != ProximityState.SAFE || isCrit) {
                    candidatesToEvaluate.add(
                        Obstacle(
                            id = "sensor_alert_${sector.name.lowercase()}",
                            trackingId = 990 + sector.ordinal,
                            objectType = ObjectType.OTHER,
                            confidence = 1.0f,
                            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                            direction = sector,
                            measuredDistanceCm = distance
                        )
                    )
                }
            }
        }

        // 3. Hardware water sensor hazard (puddle / wet floor detected by cane)
        if (sensorData.isWaterDetected || sensorData.rawWaterValue > 500) {
            candidatesToEvaluate.add(
                Obstacle(
                    id = "sensor_alert_water",
                    trackingId = 998,
                    objectType = ObjectType.WATER,
                    confidence = 1.0f,
                    boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                    direction = Direction.CENTER,
                    measuredDistanceCm = 0
                )
            )
        }

        if (candidatesToEvaluate.isEmpty()) return null

        return evaluateCandidatesAndFormatAlert(candidatesToEvaluate, language)
    }

    /**
     * Standalone Sensor Telemetry Pipeline:
     * - Does NOT call objectTracker.updateTracks().
     * - Does NOT re-evaluate visual detections.
     * - Evaluates ultrasonic obstacles ONLY for sectors not occupied by recent visual detections.
     */
    fun processStandaloneSensors(
        sensorData: SensorData,
        occupiedSectors: Set<Direction>,
        language: AppLanguage
    ): ObstacleAlert? {
        val candidatesToEvaluate = mutableListOf<Obstacle>()

        // 1. Evaluate unoccupied ultrasonic sensor sectors
        val sectorsToCheck = listOf(
            Triple(Direction.CENTER, sensorData.frontDistanceCm, sensorData.isFrontCritical),
            Triple(Direction.LEFT, sensorData.leftDistanceCm, sensorData.isLeftCritical),
            Triple(Direction.RIGHT, sensorData.rightDistanceCm, sensorData.isRightCritical)
        )

        for ((sector, distance, isCrit) in sectorsToCheck) {
            if (distance >= 0 && !occupiedSectors.contains(sector)) {
                val proxState = proximityStateManager.calculateProximityState(distance)
                if (proxState != ProximityState.SAFE || isCrit) {
                    candidatesToEvaluate.add(
                        Obstacle(
                            id = "sensor_alert_${sector.name.lowercase()}",
                            trackingId = 990 + sector.ordinal,
                            objectType = ObjectType.OTHER,
                            confidence = 1.0f,
                            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                            direction = sector,
                            measuredDistanceCm = distance
                        )
                    )
                }
            }
        }

        // 2. Hardware water sensor hazard
        if (sensorData.isWaterDetected || sensorData.rawWaterValue > 500) {
            candidatesToEvaluate.add(
                Obstacle(
                    id = "sensor_alert_water",
                    trackingId = 998,
                    objectType = ObjectType.WATER,
                    confidence = 1.0f,
                    boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                    direction = Direction.CENTER,
                    measuredDistanceCm = 0
                )
            )
        }

        if (candidatesToEvaluate.isEmpty()) return null

        return evaluateCandidatesAndFormatAlert(candidatesToEvaluate, language)
    }

    private fun evaluateCandidatesAndFormatAlert(
        candidatesToEvaluate: List<Obstacle>,
        language: AppLanguage
    ): ObstacleAlert? {
        val evaluated = candidatesToEvaluate.map { obs ->
            val distanceCm = obs.measuredDistanceCm ?: 200
            val proximityState = if (obs.objectType == ObjectType.WATER) {
                ProximityState.TOO_NEAR
            } else {
                proximityStateManager.calculateProximityState(distanceCm, obs.isUpperObstacle)
            }
            val severity = ObstaclePriorityManager.calculateSeverity(obs, proximityState)
            Triple(obs, proximityState, severity)
        }

        val highest = ObstaclePriorityManager.selectHighestPriority(
            evaluated.map { Pair(it.first, it.third) }
        ) ?: return null

        val (topObstacle, severity) = highest
        val tripleMatch = evaluated.first { it.first.id == topObstacle.id }
        val proximityState = tripleMatch.second

        val (displayText, speechText) = responseTemplateManager.formatObstacleAlert(
            obstacle = topObstacle,
            proximityState = proximityState,
            language = language
        )

        val candidateAlert = ObstacleAlert(
            id = "${topObstacle.id}_${proximityState.name}",
            obstacle = topObstacle,
            proximityState = proximityState,
            severity = severity,
            direction = topObstacle.direction,
            distanceCm = topObstacle.measuredDistanceCm,
            displayText = displayText,
            speechText = speechText
        )

        val (shouldAnnounce, isDistanceUpgrade) = alertCooldownManager.evaluateAnnouncement(candidateAlert)
        return if (shouldAnnounce) {
            alertCooldownManager.recordAnnouncement(candidateAlert)
            candidateAlert.copy(isDistanceUpgrade = isDistanceUpgrade)
        } else {
            null
        }
    }
}

