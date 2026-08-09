package com.visionmate.pro.ai

import com.visionmate.pro.model.AppLanguage
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

    fun processFrameAndSensors(
        rawDetections: List<com.visionmate.pro.model.Obstacle>,
        sensorData: SensorData,
        systemHealth: SystemHealth,
        language: AppLanguage
    ): ObstacleAlert? {

        if (!systemHealth.bluetooth.isReady) {
            return null
        }

        // 1. Update tracks
        val trackedDetections = objectTracker.updateTracks(rawDetections)

        // 2. Fuse distance for each detection
        val fusedObstacles = trackedDetections.map { obs ->
            DistanceFusion.fuseDistance(obs, sensorData)
        }

        // 3. Evaluate proximity state & priority for each
        val evaluated = fusedObstacles.map { obs ->
            val distanceCm = obs.measuredDistanceCm ?: sensorData.frontDistanceCm
            val proximityState = proximityStateManager.calculateProximityState(distanceCm, obs.isUpperObstacle)
            val severity = ObstaclePriorityManager.calculateSeverity(obs, proximityState)
            Triple(obs, proximityState, severity)
        }

        val highest = ObstaclePriorityManager.selectHighestPriority(
            evaluated.map { Pair(it.first, it.third) }
        ) ?: return null

        val (topObstacle, severity) = highest
        val tripleMatch = evaluated.first { it.first.id == topObstacle.id }
        val proximityState = tripleMatch.second

        // 4. Generate alert texts
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

        // 5. Cooldown check
        return if (alertCooldownManager.shouldAnnounce(candidateAlert)) {
            alertCooldownManager.recordAnnouncement(candidateAlert)
            candidateAlert
        } else {
            null
        }
    }
}
