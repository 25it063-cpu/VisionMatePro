package com.visionmate.pro.ai

import com.visionmate.pro.model.AlertSeverity
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.ProximityState

object ObstaclePriorityManager {

    fun calculateSeverity(obstacle: Obstacle, proximityState: ProximityState): AlertSeverity {
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
                ObjectType.POLE, ObjectType.WALL, ObjectType.WATER -> AlertSeverity.HIGH
                else -> AlertSeverity.MEDIUM
            }
        }

        return AlertSeverity.LOW
    }

    fun selectHighestPriority(obstacles: List<Pair<Obstacle, AlertSeverity>>): Pair<Obstacle, AlertSeverity>? {
        if (obstacles.isEmpty()) return null

        return obstacles.maxByOrNull { (obs, severity) ->
            var score = when (severity) {
                AlertSeverity.EMERGENCY -> 1000
                AlertSeverity.IMMEDIATE_DANGER -> 800
                AlertSeverity.HIGH -> 600
                AlertSeverity.MEDIUM -> 400
                AlertSeverity.LOW -> 200
            }

            // Bonus for center direction
            if (obs.direction == com.visionmate.pro.model.Direction.CENTER) score += 50
            // Bonus for staircase or tree branch
            if (obs.objectType == ObjectType.STAIRCASE) score += 100
            if (obs.objectType == ObjectType.TREE_BRANCH) score += 90

            score
        }
    }
}
