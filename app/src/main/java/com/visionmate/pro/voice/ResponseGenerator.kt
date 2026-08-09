package com.visionmate.pro.voice

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.ObstacleAlert

class ResponseGenerator {

    fun generateBatteryAlert(percent: Int, language: AppLanguage): Pair<String, String> {
        val display = "CANE BATTERY: $percent%"
        val speech = when (language) {
            AppLanguage.ENGLISH -> "Cane battery is low. Approximately $percent percent remaining."
            AppLanguage.TAMIL -> "பிரம்பு பேட்டரி $percent சதவீதம் மட்டுமே உள்ளது."
            AppLanguage.HINDI -> "छड़ी की बैटरी $percent प्रतिशत बची है।"
        }
        return Pair(display, speech)
    }

    fun generateConnectionLostAlert(language: AppLanguage): Pair<String, String> {
        val display = "BLUETOOTH DISCONNECTED"
        val speech = when (language) {
            AppLanguage.ENGLISH -> "Warning. Cane connection lost."
            AppLanguage.TAMIL -> "எச்சரிக்கை. பிரம்பு இணைப்பு துண்டிக்கப்பட்டது."
            AppLanguage.HINDI -> "चेतावनी। छड़ी का कनेक्शन टूट गया।"
        }
        return Pair(display, speech)
    }
}
