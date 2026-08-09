package com.visionmate.pro.model

enum class AlertSeverity {
    EMERGENCY,
    IMMEDIATE_DANGER,
    HIGH,
    MEDIUM,
    LOW
}

data class ObstacleAlert(
    val id: String,
    val obstacle: Obstacle,
    val proximityState: ProximityState,
    val severity: AlertSeverity,
    val direction: Direction,
    val distanceCm: Int?,
    val displayText: String,
    val speechText: String,
    val timestamp: Long = System.currentTimeMillis()
)
