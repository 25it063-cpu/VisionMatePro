package com.visionmate.pro.navigation

import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.ProximityState

class ContextAwareNavigationManager {

    private var activeDestination: String? = null

    fun setDestination(destination: String) {
        activeDestination = destination
    }

    fun clearDestination() {
        activeDestination = null
    }

    /**
     * Combines navigation route instruction with current environmental detections.
     */
    fun buildContextAwarePrompt(
        rawNavigationStep: String,
        nearbyObstacles: List<Obstacle>,
        proximityState: ProximityState
    ): String {
        // If immediate danger or STOP state, safety manager overrides navigation completely!
        if (proximityState == ProximityState.TOO_NEAR) {
            return ""
        }

        if (nearbyObstacles.isEmpty()) {
            return rawNavigationStep
        }

        val primaryObs = nearbyObstacles.firstOrNull()
        return if (primaryObs != null) {
            val obsText = primaryObs.objectType.name.lowercase().replace("_", " ")
            val dirText = primaryObs.direction.name.lowercase()
            "$rawNavigationStep There is a $obsText on your $dirText."
        } else {
            rawNavigationStep
        }
    }
}
