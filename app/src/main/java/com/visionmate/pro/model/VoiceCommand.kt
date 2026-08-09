package com.visionmate.pro.model

enum class CommandIntent {
    QUERY_CURRENT_OBSTACLE,
    NAVIGATE,
    OPEN_MAPS,
    EMERGENCY,
    CALL_EMERGENCY,
    CHANGE_EMERGENCY_CONTACT,
    CHANGE_LANGUAGE,
    BATTERY_STATUS,
    FIND_CANE,
    CANCEL_SOS,
    UNKNOWN
}

data class VoiceCommand(
    val rawText: String,
    val intent: CommandIntent,
    val parameters: Map<String, String> = emptyMap(),
    val languageCode: String = "en"
)
