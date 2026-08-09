package com.visionmate.pro.model

data class SensorData(
    val frontDistanceCm: Int = 180,
    val leftDistanceCm: Int = 200,
    val rightDistanceCm: Int = 200,
    val isWaterDetected: Boolean = false,
    val caneBatteryPercent: Int = 87,
    val isPhysicalSosPressed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
