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
            AppLanguage.TELUGU -> "స్టిక్ బ్యాటరీ తక్కువగా ఉంది. సుమారు $percent శాతం మిగిలి ఉంది."
            AppLanguage.MALAYALAM -> "സ്റ്റിക്ക് ബാറ്ററി കുറവാണ്. ഏകദേശം $percent ശതമാനം ബാക്കിയുണ്ട്."
        }
        return Pair(display, speech)
    }

    fun generateConnectionLostAlert(language: AppLanguage): Pair<String, String> {
        val display = "BLUETOOTH DISCONNECTED"
        val speech = when (language) {
            AppLanguage.ENGLISH -> "Warning. Cane connection lost."
            AppLanguage.TAMIL -> "எச்சரிக்கை. பிரம்பு இணைப்பு துண்டிக்கப்பட்டது."
            AppLanguage.HINDI -> "चेतावनी। छड़ी का कनेक्शन टूट गया।"
            AppLanguage.TELUGU -> "హెచ్చరిక. స్టిక్ కనెక్షన్ పోయింది."
            AppLanguage.MALAYALAM -> "മുന്നറിയിപ്പ്. സ്റ്റിക്ക് കണക്ഷൻ നഷ്ടപ്പെട്ടു."
        }
        return Pair(display, speech)
    }
}
