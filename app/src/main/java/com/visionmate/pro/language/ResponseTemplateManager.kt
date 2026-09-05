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
        val dist = obstacle.measuredDistanceCm

        if (proximityState == ProximityState.TOO_NEAR) {
            val display = when (language) {
                AppLanguage.TAMIL -> "நில்லுங்கள்"
                AppLanguage.HINDI -> "रुको"
                AppLanguage.TELUGU -> "ఆగండి"
                AppLanguage.MALAYALAM -> "നിൽക്കൂ"
                else -> "STOP"
            }
            
            val speech = when (language) {
                AppLanguage.TAMIL -> "நில்லுங்கள். $localizedName மிக அருகில் உள்ளது."
                AppLanguage.HINDI -> "रुको। $localizedName बहुत पास है।"
                AppLanguage.TELUGU -> "ఆగండి. $localizedName చాలా దగ్గరగా ఉంది."
                AppLanguage.MALAYALAM -> "നിൽക്കൂ. $localizedName തൊട്ടടുത്തുണ്ട്."
                else -> "Stop. $localizedName is too near."
            }
            return Pair(display, speech)
        }

        val dirDisplay = when (obstacle.direction) {
            Direction.LEFT -> "$localizedName left".uppercase()
            Direction.RIGHT -> "$localizedName right".uppercase()
            else -> "$localizedName ahead".uppercase()
        }

        val speech = buildString {
            when (language) {
                AppLanguage.ENGLISH -> {
                    append(localizedName.replaceFirstChar { it.uppercase() })
                    append(" detected")
                    if (dist != null) append(" at $dist centimeters.")
                    else append(" ahead.")
                }
                AppLanguage.TAMIL -> {
                    append("$localizedName கண்டறியப்பட்டது. ")
                    if (dist != null) append("$dist சென்டிமீட்டரில் உள்ளது.")
                }
                AppLanguage.HINDI -> {
                    append("$localizedName का पता चला है। ")
                    if (dist != null) append("$dist सेंटीमीटर पर है।")
                }
                AppLanguage.TELUGU -> {
                    append("$localizedName గుర్తించబడింది. ")
                    if (dist != null) append("$dist సెంటీమీటర్ల దూరంలో ఉంది.")
                }
                AppLanguage.MALAYALAM -> {
                    append("$localizedName കണ്ടെത്തി. ")
                    if (dist != null) append("$dist സെന്റിമീറ്റർ ദൂരത്തിൽ ഉണ്ട്.")
                }
            }
        }

        return Pair(dirDisplay, speech)
    }
}
