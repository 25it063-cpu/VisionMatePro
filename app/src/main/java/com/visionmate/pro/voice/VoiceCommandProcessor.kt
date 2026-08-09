package com.visionmate.pro.voice

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.CommandIntent
import com.visionmate.pro.model.VoiceCommand
import com.visionmate.pro.language.LanguageManager

sealed class VoiceActionResult {
    data class SpeakResponse(val message: String) : VoiceActionResult()
    object TriggerFindCane : VoiceActionResult()
    object TriggerSos : VoiceActionResult()
    object CancelSos : VoiceActionResult()
    data class QueryScene(val text: String) : VoiceActionResult()
    data class ChangeLanguage(val language: AppLanguage) : VoiceActionResult()
    data class UpdateSosNumber(val number: String) : VoiceActionResult()
    data class StartNavigation(val destination: String) : VoiceActionResult()
}

class VoiceCommandProcessor(
    private val languageManager: LanguageManager
) {

    fun processCommand(command: VoiceCommand): VoiceActionResult {
        val currentLang = languageManager.currentLanguage.value

        return when (command.intent) {
            CommandIntent.NAVIGATE -> {
                val destination = command.parameters["destination"]
                if (!destination.isNullOrBlank()) {
                    VoiceActionResult.StartNavigation(destination)
                } else {
                    val msg = when (currentLang) {
                        AppLanguage.TAMIL -> "எங்கே செல்ல வேண்டும் என்று சொல்லுங்கள்."
                        AppLanguage.HINDI -> "बताइए आप कहां जाना चाहते हैं।"
                        else -> "Please tell me where you want to go."
                    }
                    VoiceActionResult.SpeakResponse(msg)
                }
            }
            CommandIntent.CHANGE_EMERGENCY_CONTACT -> {
                val number = command.parameters["number"]
                if (number != null && number.length >= 5) {
                    VoiceActionResult.UpdateSosNumber(number)
                } else {
                    val msg = when (currentLang) {
                        AppLanguage.TAMIL -> "மன்னிக்கவும், சரியான தொலைபேசி எண்ணைக் கூறவும்."
                        AppLanguage.HINDI -> "क्षमा करें, कृपया एक सही फ़ोन नंबर बताएं।"
                        else -> "Sorry, please provide a valid phone number."
                    }
                    VoiceActionResult.SpeakResponse(msg)
                }
            }
            CommandIntent.CANCEL_SOS -> VoiceActionResult.CancelSos
            CommandIntent.EMERGENCY, CommandIntent.CALL_EMERGENCY -> VoiceActionResult.TriggerSos
            CommandIntent.FIND_CANE -> VoiceActionResult.TriggerFindCane
            CommandIntent.QUERY_CURRENT_OBSTACLE -> VoiceActionResult.QueryScene(command.rawText)
            CommandIntent.CHANGE_LANGUAGE -> {
                val rawLower = command.rawText.lowercase()
                val newLang = when {
                    rawLower.contains("tamil") || rawLower.contains("தமிழ்") -> AppLanguage.TAMIL
                    rawLower.contains("hindi") || rawLower.contains("हिन्दी") -> AppLanguage.HINDI
                    else -> AppLanguage.ENGLISH
                }
                languageManager.setLanguage(newLang)
                VoiceActionResult.ChangeLanguage(newLang)
            }
            CommandIntent.BATTERY_STATUS -> {
                val msg = when (currentLang) {
                    AppLanguage.ENGLISH -> "Checking cane battery."
                    AppLanguage.TAMIL -> "பிரம்பு பேட்டரி சரிபார்க்கப்படுகிறது."
                    AppLanguage.HINDI -> "छड़ी की बैटरी जांची जा रही है।"
                }
                VoiceActionResult.SpeakResponse(msg)
            }
            else -> {
                val msg = when (currentLang) {
                    AppLanguage.ENGLISH -> "I didn't quite catch that. Try asking what's in front of you."
                    AppLanguage.TAMIL -> "எனக்கு சரியாக புரியவில்லை. மீண்டும் முயற்சிக்கவும்."
                    AppLanguage.HINDI -> "मुझे समझ नहीं आया। फिर से प्रयास करें।"
                }
                VoiceActionResult.SpeakResponse(msg)
            }
        }
    }
}
