package com.visionmate.pro.language

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.ProximityState

class ResponseTemplateManager {

    /**
     * Formats the coordinated YOLO + Sensor alert.
     * Fulfills Requirement 7: "[Object] detected at [Distance] centimeters"
     */
    fun formatObstacleAlert(
        obstacle: Obstacle,
        proximityState: ProximityState,
        language: AppLanguage
    ): Pair<String, String> { // Pair(DisplayText, SpeechText)

        val localizedName = LocalizedObjectNames.getLocalizedName(obstacle.objectType, language)
        val dist = obstacle.measuredDistanceCm

        // Pre-calculate distance string for different languages
        val distText = if (dist != null) {
            when (language) {
                AppLanguage.TAMIL -> "$dist செண்டிமீட்டர்"
                AppLanguage.HINDI -> "$dist सेंटीमीटर"
                AppLanguage.TELUGU -> "$dist సెంటీమీటర్లు"
                AppLanguage.MALAYALAM -> "$dist సెന്റിമീറ്റർ"
                else -> "$dist centimeters"
            }
        } else ""

        val dirSuffixEnglish = when (obstacle.direction) {
            Direction.LEFT -> "on the left"
            Direction.RIGHT -> "on the right"
            Direction.CENTER -> "ahead"
        }

        val display = when (obstacle.direction) {
            Direction.LEFT -> "$localizedName left".uppercase()
            Direction.RIGHT -> "$localizedName right".uppercase()
            Direction.CENTER -> "$localizedName ahead".uppercase()
        }

        val speech = buildString {
            when (language) {
                AppLanguage.ENGLISH -> {
                    append(localizedName.replaceFirstChar { it.uppercase() })
                    append(" detected")
                    if (dist != null) {
                        append(" at $distText $dirSuffixEnglish.")
                    } else {
                        append(" $dirSuffixEnglish.")
                    }
                }
                AppLanguage.TAMIL -> {
                    val dirTamil = when (obstacle.direction) {
                        Direction.LEFT -> "இடதுபுறத்தில்"
                        Direction.RIGHT -> "வலதுபுறத்தில்"
                        Direction.CENTER -> "முன்னே"
                    }
                    append("$localizedName கண்டறியப்பட்டது. ")
                    if (dist != null) {
                        append("$dirTamil $distText.")
                    } else {
                        append("$dirTamil.")
                    }
                }
                AppLanguage.HINDI -> {
                    val dirHindi = when (obstacle.direction) {
                        Direction.LEFT -> "बाईं ओर"
                        Direction.RIGHT -> "दाईं ओर"
                        Direction.CENTER -> "सामने"
                    }
                    append("$localizedName का पता चला है। ")
                    if (dist != null) {
                        append("$dirHindi $distText पर है।")
                    } else {
                        append("$dirHindi है।")
                    }
                }
                AppLanguage.TELUGU -> {
                    val dirTelugu = when (obstacle.direction) {
                        Direction.LEFT -> "ఎడమవైపు"
                        Direction.RIGHT -> "కుడివైపు"
                        Direction.CENTER -> "ముందు"
                    }
                    append("$localizedName గుర్తించబడింది. ")
                    if (dist != null) {
                        append("$dirTelugu $distText లో ఉంది.")
                    } else {
                        append("$dirTelugu ఉంది.")
                    }
                }
                AppLanguage.MALAYALAM -> {
                    val dirMalayalam = when (obstacle.direction) {
                        Direction.LEFT -> "ഇടതുവശത്ത്"
                        Direction.RIGHT -> "വലതുവശത്ത്"
                        Direction.CENTER -> "മുന്നിൽ"
                    }
                    append("$localizedName കണ്ടെത്തി. ")
                    if (dist != null) {
                        append("$dirMalayalam $distText.")
                    } else {
                        append("$dirMalayalam.")
                    }
                }
            }
        }

        return Pair(display, speech)
    }
}
