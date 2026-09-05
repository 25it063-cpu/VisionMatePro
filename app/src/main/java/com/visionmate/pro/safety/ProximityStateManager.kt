package com.visionmate.pro.safety

import com.visionmate.pro.model.ProximityState

class ProximityStateManager(
    var cautionRangeCm: Int = 70,   // Caution at 0.7 meters
    var criticalRangeCm: Int = 30   // Danger at 0.3 meters
) {

    fun calculateProximityState(distanceCm: Int?, isUpperObstacle: Boolean = false): ProximityState {
        if (isUpperObstacle) {
            return ProximityState.NEAR
        }

        if (distanceCm == null) {
            return ProximityState.SAFE
        }

        // Some sensors return 0 when an object is touching the sensor.
        // We treat anything from 0 to 30cm as TOO_NEAR (Danger).
        return when {
            distanceCm in 0..criticalRangeCm -> ProximityState.TOO_NEAR
            distanceCm <= cautionRangeCm -> ProximityState.NEAR
            else -> ProximityState.SAFE
        }
    }
}
