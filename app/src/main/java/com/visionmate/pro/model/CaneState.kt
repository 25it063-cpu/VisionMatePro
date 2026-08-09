package com.visionmate.pro.model

enum class BatteryLevelState {
    NORMAL,
    LOW,
    CRITICAL
}

data class CaneState(
    val connectionState: ConnectionState = ConnectionState(),
    val sensorData: SensorData = SensorData(),
    val batteryState: BatteryLevelState = BatteryLevelState.NORMAL
)
