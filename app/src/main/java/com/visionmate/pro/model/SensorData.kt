package com.visionmate.pro.model

data class SensorData(
    val frontDistanceCm: Int = -1,
    val leftDistanceCm: Int = -1,
    val rightDistanceCm: Int = -1,
    val isWaterDetected: Boolean = false,
    val rawWaterValue: Int = 0,
    val isFrontCritical: Boolean = false,
    val isLeftCritical: Boolean = false,
    val isRightCritical: Boolean = false,
    val sequenceNumber: Long = 0L,
    val caneBatteryPercent: Int = 87,
    val isPhysicalSosPressed: Boolean = false,
    val timestamp: Long = 0L
) {
    fun isFresh(maxAgeMs: Long = 1500L): Boolean {
        return timestamp > 0L && (System.currentTimeMillis() - timestamp) <= maxAgeMs
    }

    companion object {
        fun invalid(): SensorData = SensorData(
            frontDistanceCm = -1,
            leftDistanceCm = -1,
            rightDistanceCm = -1,
            isWaterDetected = false,
            rawWaterValue = 0,
            isFrontCritical = false,
            isLeftCritical = false,
            isRightCritical = false,
            sequenceNumber = 0L,
            caneBatteryPercent = 0,
            isPhysicalSosPressed = false,
            timestamp = 0L
        )
    }
}

