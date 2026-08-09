package com.visionmate.pro.safety

import com.visionmate.pro.model.ProximityState

class ProximityStateManager(
    var cautionRangeCm: Int = 150,  // Configurable, e.g., 1.5m
    var criticalRangeCm: Int = 30   // Configurable, e.g., 0.3m
) {

    fun calculateProximityState(distanceCm: Int?, isUpperObstacle: Boolean = false): ProximityState {
        if (isUpperObstacle) {
            // Upper obstacle detected via camera visual bounding box
            return ProximityState.NEAR
        }

        if (distanceCm == null) {
            return ProximityState.SAFE
        }

        return when {
            distanceCm <= criticalRangeCm -> ProximityState.TOO_NEAR
            distanceCm <= cautionRangeCm -> ProximityState.NEAR
            else -> ProximityState.SAFE
        }
    }
}
