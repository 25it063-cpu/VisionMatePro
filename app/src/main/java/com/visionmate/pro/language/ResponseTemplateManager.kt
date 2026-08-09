package com.visionmate.pro.language

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.ProximityState

class ResponseTemplateManager {

    fun formatObstacleAlert(
        obstacle: Obstacle,
        proximityState: ProximityState,
        language: AppLanguage
    ): Pair<String, String> { // Pair(DisplayText, SpeechText)

        val localizedName = LocalizedObjectNames.getLocalizedName(obstacle.objectType, language)

        if (proximityState == ProximityState.TOO_NEAR) {
            val display = when (language) {
                AppLanguage.ENGLISH -> "STOP"
                AppLanguage.TAMIL -> "நில்லுங்கள்"
                AppLanguage.HINDI -> "रुको"
            }
            val speech = when (language) {
                AppLanguage.ENGLISH -> "Stop. $localizedName ahead."
                AppLanguage.TAMIL -> "நில்லுங்கள். முன்னே $localizedName உள்ளது."
                AppLanguage.HINDI -> "रुको। आगे $localizedName है।"
            }
            return Pair(display, speech)
        }

        val dirDisplay = when (obstacle.direction) {
            Direction.LEFT -> when (language) {
                AppLanguage.ENGLISH -> "$localizedName on left".uppercase()
                AppLanguage.TAMIL -> "இடதுபுறத்தில் $localizedName".uppercase()
                AppLanguage.HINDI -> "बाईं ओर $localizedName".uppercase()
            }
            Direction.RIGHT -> when (language) {
                AppLanguage.ENGLISH -> "$localizedName on right".uppercase()
                AppLanguage.TAMIL -> "வலதுபுறத்தில் $localizedName".uppercase()
                AppLanguage.HINDI -> "दाहिनी ओर $localizedName".uppercase()
            }
            Direction.CENTER -> when (language) {
                AppLanguage.ENGLISH -> "$localizedName ahead".uppercase()
                AppLanguage.TAMIL -> "முன்னே $localizedName".uppercase()
                AppLanguage.HINDI -> "आगे $localizedName".uppercase()
            }
        }

        val speech = buildString {
            when (language) {
                AppLanguage.ENGLISH -> {
                    append(localizedName.replaceFirstChar { it.uppercase() })
                    when (obstacle.direction) {
                        Direction.LEFT -> append(" on your left")
                        Direction.RIGHT -> append(" on your right")
                        Direction.CENTER -> append(" ahead")
                    }
                    if (obstacle.measuredDistanceCm != null) {
                        val m = obstacle.measuredDistanceCm / 100.0
                        if (m >= 1.0) append(" at ${"%.1f".format(m)} meters.")
                        else append(" at ${obstacle.measuredDistanceCm} centimeters.")
                    } else {
                        append(".")
                    }
                }
                AppLanguage.TAMIL -> {
                    when (obstacle.direction) {
                        Direction.LEFT -> append("உங்கள் இடதுபுறத்தில் ")
                        Direction.RIGHT -> append("உங்கள் வலதுபுறத்தில் ")
                        Direction.CENTER -> append("முன்னே ")
                    }
                    if (obstacle.measuredDistanceCm != null) {
                        val m = obstacle.measuredDistanceCm / 100.0
                        if (m >= 1.0) append("${"%.1f".format(m)} மீட்டரில் ")
                        else append("${obstacle.measuredDistanceCm} சென்டிமீட்டரில் ")
                    }
                    append("$localizedName உள்ளது.")
                }
                AppLanguage.HINDI -> {
                    when (obstacle.direction) {
                        Direction.LEFT -> append("आपकी बाईं ओर ")
                        Direction.RIGHT -> append("आपकी दाहिनी ओर ")
                        Direction.CENTER -> append("आगे ")
                    }
                    if (obstacle.measuredDistanceCm != null) {
                        val m = obstacle.measuredDistanceCm / 100.0
                        if (m >= 1.0) append("${"%.1f".format(m)} मीटर पर ")
                        else append("${obstacle.measuredDistanceCm} सेंटीमीटर पर ")
                    }
                    append("$localizedName है।")
                }
            }
        }

        return Pair(dirDisplay, speech)
    }
}
