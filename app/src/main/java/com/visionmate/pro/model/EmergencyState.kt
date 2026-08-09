package com.visionmate.pro.model

enum class EmergencyStatus {
    IDLE,
    PREPARING,
    AWAITING_CONFIRMATION,
    CANCELLED,
    SENDING,
    SENT,
    FAILED
}

data class EmergencyState(
    val status: EmergencyStatus = EmergencyStatus.IDLE,
    val emergencyContactName: String = "Guardian Contact",
    val emergencyContactNumber: String = "+1234567890",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val remainingSeconds: Int = 10,
    val message: String = ""
)
