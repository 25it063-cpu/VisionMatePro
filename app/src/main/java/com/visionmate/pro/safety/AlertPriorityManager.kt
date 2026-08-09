package com.visionmate.pro.safety

import com.visionmate.pro.model.AlertSeverity

class AlertPriorityManager {

    fun isHigherPriority(newSeverity: AlertSeverity, currentSeverity: AlertSeverity?): Boolean {
        if (currentSeverity == null) return true
        return getOrdinal(newSeverity) > getOrdinal(currentSeverity)
    }

    private fun getOrdinal(severity: AlertSeverity): Int {
        return when (severity) {
            AlertSeverity.EMERGENCY -> 5
            AlertSeverity.IMMEDIATE_DANGER -> 4
            AlertSeverity.HIGH -> 3
            AlertSeverity.MEDIUM -> 2
            AlertSeverity.LOW -> 1
        }
    }
}
