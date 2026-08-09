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

    fun shouldAnnounce(newAlert: ObstacleAlert): Boolean {
        val trackingId = newAlert.obstacle.trackingId
        val previousRecord = recentAlertsMap[trackingId]

        if (previousRecord == null) {
            return true
        }

        val now = System.currentTimeMillis()
        val timeElapsed = now - previousRecord.timestamp

        // Proximity escalation overrides cooldown (e.g. NEAR -> TOO_NEAR)
        if (newAlert.proximityState == ProximityState.TOO_NEAR &&
            previousRecord.alert.proximityState != ProximityState.TOO_NEAR) {
            return true
        }

        // Significant distance change (> 35cm difference) overrides cooldown
        val prevDist = previousRecord.alert.distanceCm
        val newDist = newAlert.distanceCm
        if (prevDist != null && newDist != null && (prevDist - newDist) >= 35) {
            return true
        }

        return timeElapsed > cooldownWindowMs
    }

    fun recordAnnouncement(alert: ObstacleAlert) {
        recentAlertsMap[alert.obstacle.trackingId] = AlertCooldownRecord(alert)
    }

    fun reset() {
        recentAlertsMap.clear()
    }
}
