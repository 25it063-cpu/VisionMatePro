package com.visionmate.pro.safety

import com.visionmate.pro.model.AlertSeverity
import com.visionmate.pro.model.EmergencyState
import com.visionmate.pro.model.EmergencyStatus
import com.visionmate.pro.model.ObstacleAlert
import com.visionmate.pro.model.SystemHealth

sealed class SystemEvent {
    data class Emergency(val state: EmergencyState) : SystemEvent()
    data class ImmediateObstacle(val alert: ObstacleAlert) : SystemEvent()
    data class SystemFailure(val title: String, val message: String) : SystemEvent()
    data class Navigation(val text: String) : SystemEvent()
    data class NormalAssistant(val text: String) : SystemEvent()
}

class SafetyManager {

    /**
     * Evaluates highest priority system output.
     * Guaranteed priority: Emergency > Immediate Obstacle > System Failure > Navigation > AI Chat
     */
    fun evaluatePriorityEvent(
        emergencyState: EmergencyState,
        obstacleAlert: ObstacleAlert?,
        systemHealth: SystemHealth,
        navigationPrompt: String?,
        normalAssistantResponse: String?
    ): SystemEvent? {

        // 1. Emergency mode has absolute #1 priority
        if (emergencyState.status != EmergencyStatus.IDLE) {
            return SystemEvent.Emergency(emergencyState)
        }

        // 2. Immediate obstacle / STOP / Emergency severity
        if (obstacleAlert != null && (obstacleAlert.severity == AlertSeverity.EMERGENCY || obstacleAlert.severity == AlertSeverity.IMMEDIATE_DANGER)) {
            return SystemEvent.ImmediateObstacle(obstacleAlert)
        }

        // 3. Critical system failure (e.g. Bluetooth connection lost)
        if (!systemHealth.bluetooth.isReady) {
            return SystemEvent.SystemFailure("Bluetooth Disconnected", "Warning. Cane connection lost.")
        }

        // 4. Vision failure
        if (!systemHealth.cameraStream.isReady || !systemHealth.aiVision.isReady) {
            return SystemEvent.SystemFailure("Vision Ready Off", "Vision detection unavailable.")
        }

        // 5. High/Medium obstacle alerts
        if (obstacleAlert != null && (obstacleAlert.severity == AlertSeverity.HIGH || obstacleAlert.severity == AlertSeverity.MEDIUM)) {
            return SystemEvent.ImmediateObstacle(obstacleAlert)
        }

        // 6. Navigation instructions
        if (navigationPrompt != null && navigationPrompt.trim().isNotEmpty()) {
            return SystemEvent.Navigation(navigationPrompt)
        }

        // 7. Normal AI assistant response
        if (normalAssistantResponse != null && normalAssistantResponse.trim().isNotEmpty()) {
            return SystemEvent.NormalAssistant(normalAssistantResponse)
        }

        return null
    }
}
