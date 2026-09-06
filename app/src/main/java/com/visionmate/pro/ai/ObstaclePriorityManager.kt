package com.visionmate.pro.ai

import com.visionmate.pro.model.AlertSeverity
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.ProximityState

object ObstaclePriorityManager {

    fun calculateSeverity(obstacle: Obstacle, proximityState: ProximityState): AlertSeverity {
        if (obstacle.objectType == ObjectType.WATER) {
            return AlertSeverity.EMERGENCY
        }

        if (proximityState == ProximityState.TOO_NEAR) {
            return if (obstacle.objectType == ObjectType.STAIRCASE || obstacle.objectType == ObjectType.VEHICLE) {
                AlertSeverity.EMERGENCY
            } else {
                AlertSeverity.IMMEDIATE_DANGER
            }
        }

        if (proximityState == ProximityState.NEAR) {
            return when (obstacle.objectType) {
                ObjectType.STAIRCASE, ObjectType.VEHICLE, ObjectType.TREE_BRANCH -> AlertSeverity.HIGH
                ObjectType.POLE, ObjectType.WALL -> AlertSeverity.HIGH
                else -> AlertSeverity.MEDIUM
            }
        }

        return AlertSeverity.LOW
    }

    fun selectHighestPriority(obstacles: List<Pair<Obstacle, AlertSeverity>>): Pair<Obstacle, AlertSeverity>? {
        if (obstacles.isEmpty()) return null

        return obstacles.maxByOrNull { (obs, severity) ->
            var score = when (severity) {
                AlertSeverity.EMERGENCY -> 10000
                AlertSeverity.IMMEDIATE_DANGER -> 8000
                AlertSeverity.HIGH -> 6000
                AlertSeverity.MEDIUM -> 4000
                AlertSeverity.LOW -> 2000
            }

            // Distance component: closer obstacles get higher score within same severity tier
            val distance = obs.measuredDistanceCm ?: 200
            score += (200 - distance.coerceIn(0, 200)) * 5

            // Bonus for staircase or tree branch (critical obstacle types)
            if (obs.objectType == ObjectType.STAIRCASE) score += 50
            if (obs.objectType == ObjectType.TREE_BRANCH) score += 40

            // If a visual detection and a raw sensor candidate have the EXACT same severity and distance,
            // prefer the visual detection because it contains the descriptive object classification
            if (obs.objectType != ObjectType.OTHER) score += 5

            score
        }
    }
}
