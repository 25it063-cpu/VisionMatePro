package com.visionmate.pro.safety

import com.visionmate.pro.model.ObstacleAlert
import com.visionmate.pro.model.ProximityState

class AlertCooldownRecord(
    val alert: ObstacleAlert,
    val timestamp: Long = System.currentTimeMillis()
)

class AlertCooldownManager(
    private val cooldownWindowMs: Long = 4000L
) {

    private val recentAlertsMap = mutableMapOf<Int, AlertCooldownRecord>()

    fun evaluateAnnouncement(newAlert: ObstacleAlert): Pair<Boolean, Boolean> { // Pair(shouldAnnounce, isDistanceUpgrade)
        val trackingId = newAlert.obstacle.trackingId
        val previousRecord = recentAlertsMap[trackingId]

        if (previousRecord == null) {
            return Pair(true, false)
        }

        val now = System.currentTimeMillis()
        val timeElapsed = now - previousRecord.timestamp

        // 1. Proximity escalation overrides cooldown (e.g. NEAR -> TOO_NEAR)
        if (newAlert.proximityState == ProximityState.TOO_NEAR &&
            previousRecord.alert.proximityState != ProximityState.TOO_NEAR) {
            return Pair(true, false)
        }

        // 2. Distance upgrade: Previous announcement was distance-less (null), but new alert has a valid physical distance
        // Allows the valid fused alert (e.g. "Person at 82 cm") to immediately supersede a prior "Person detected ahead"
        val prevDist = previousRecord.alert.distanceCm
        val newDist = newAlert.distanceCm
        if (prevDist == null && newDist != null) {
            return Pair(true, true)
        }

        // 3. Significant distance change (> 35cm approach) overrides cooldown
        if (prevDist != null && newDist != null && (prevDist - newDist) >= 35) {
            return Pair(true, false)
        }

        return Pair(timeElapsed > cooldownWindowMs, false)
    }

    fun shouldAnnounce(newAlert: ObstacleAlert): Boolean {
        return evaluateAnnouncement(newAlert).first
    }

    fun recordAnnouncement(alert: ObstacleAlert) {
        recentAlertsMap[alert.obstacle.trackingId] = AlertCooldownRecord(alert)
    }

    fun reset() {
        recentAlertsMap.clear()
    }
}
