package com.visionmate.pro.language

import com.visionmate.pro.model.AppLanguage

object TranslationManager {

    fun getSystemString(key: String, language: AppLanguage): String {
        return when (key) {
            "PATH_CLEAR" -> when (language) {
                AppLanguage.ENGLISH -> "Path clear."
                AppLanguage.TAMIL -> "பாதை தெளிவாக உள்ளது."
                AppLanguage.HINDI -> "रास्ता साफ है।"
                AppLanguage.TELUGU -> "దారి ఖాళీగా ఉంది."
                AppLanguage.MALAYALAM -> "പാത വ്യക്തമാണ്."
            }
            "BT_DISCONNECTED" -> when (language) {
                AppLanguage.ENGLISH -> "Warning. Cane connection lost."
                AppLanguage.TAMIL -> "எச்சரிக்கை. பிரம்பு இணைப்பு துண்டிக்கப்பட்டது."
                AppLanguage.HINDI -> "चेतावनी। छड़ी का कनेक्शन टूट गया।"
                AppLanguage.TELUGU -> "హెచ్చరిక. స్టిక్ కనెక్షన్ పోయింది."
                AppLanguage.MALAYALAM -> "മുന്നറിയിപ്പ്. സ്റ്റിക്ക് കണക്ഷൻ നഷ്ടപ്പെട്ടു."
            }
            "VISION_UNAVAILABLE" -> when (language) {
                AppLanguage.ENGLISH -> "Vision detection unavailable."
                AppLanguage.TAMIL -> "கேமரா பார்வை கிடைக்கவில்லை."
                AppLanguage.HINDI -> "दृष्टि पहचान उपलब्ध नहीं है।"
                AppLanguage.TELUGU -> "విజన్ డిటెక్షన్ అందుబాటులో లేదు."
                AppLanguage.MALAYALAM -> "വിഷൻ ഡിറ്റക്ഷൻ ലഭ്യമല്ല."
            }
            "BATTERY_LOW" -> when (language) {
                AppLanguage.ENGLISH -> "Cane battery is low. Approximately 20 percent remaining."
                AppLanguage.TAMIL -> "பிரம்பு பேட்டரி குறைவாக உள்ளது."
                AppLanguage.HINDI -> "छड़ी की बैटरी कम है।"
                AppLanguage.TELUGU -> "స్టిక్ బ్యాటరీ తక్కువగా ఉంది."
                AppLanguage.MALAYALAM -> "സ്റ്റിക്ക് ബാറ്ററി കുറവാണ്."
            }
            "FIND_CANE_ACTIVATED" -> when (language) {
                AppLanguage.ENGLISH -> "Activating cane locator."
                AppLanguage.TAMIL -> "பிரம்பு இருப்பிட ஒலி இயக்கப்படுகிறது."
                AppLanguage.HINDI -> "छड़ी लोकेटर सक्रिय किया जा रहा है।"
                AppLanguage.TELUGU -> "స్టిక్ లోకేటర్ యాక్టివేట్ అవుతోంది."
                AppLanguage.MALAYALAM -> "സ്റ്റിക്ക് ലൊക്കേറ്റർ സജീവമാക്കുന്നു."
            }
            "EMERGENCY_READY" -> when (language) {
                AppLanguage.ENGLISH -> "Emergency alert ready. Say cancel to stop."
                AppLanguage.TAMIL -> "அவசர எச்சரிக்கை தயார். நிறுத்த கேன்சல் என்று சொல்லுங்கள்."
                AppLanguage.HINDI -> "आपतकालीन चेतावनी तैयार है। रोकने के लिए कैंसिल कहें।"
                AppLanguage.TELUGU -> "అత్యవసర హెచ్చరిక సిద్ధంగా ఉంది. ఆపడానికి క్యాన్సిల్ అని చెప్పండి."
                AppLanguage.MALAYALAM -> "അടിയന്തിര മുന്നറിയിപ്പ് തയ്യാറാണ്. നിർത്താൻ ക്യാൻസൽ എന്ന് പറയുക."
            }
            "EMERGENCY_SENT" -> when (language) {
                AppLanguage.ENGLISH -> "Emergency alert sent."
                AppLanguage.TAMIL -> "அவசர எச்சரிக்கை அனுப்பப்பட்டது."
                AppLanguage.HINDI -> "आपतकालीन अलर्ट भेज दिया गया है।"
                AppLanguage.TELUGU -> "అత్యవసర హెచ్చరిక పంపబడింది."
                AppLanguage.MALAYALAM -> "അടിയന്തിര മുന്നറിയിപ്പ് അയച്ചു."
            }
            "EMERGENCY_CANCELLED" -> when (language) {
                AppLanguage.ENGLISH -> "Emergency alert cancelled."
                AppLanguage.TAMIL -> "அவசர எச்சரிக்கை ரத்து செய்யப்பட்டது."
                AppLanguage.HINDI -> "आपतकालीन अलर्ट रद्द कर दिया गया।"
                AppLanguage.TELUGU -> "అత్యవసర హెచ్చరిక రద్దు చేయబడింది."
                AppLanguage.MALAYALAM -> "അടിയന്തിര മുന്നറിയിപ്പ് റദ്ദാക്കി."
            }
            else -> key
        }
    }
}
