package com.visionmate.pro.voice

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.CommandIntent
import com.visionmate.pro.model.VoiceCommand
import com.visionmate.pro.language.LanguageManager

class VoiceCommandProcessor(
    private val languageManager: LanguageManager
) {

    fun processCommand(command: VoiceCommand): VoiceActionResult {
        val currentLang = languageManager.currentLanguage.value

        return when (command.intent) {
            CommandIntent.GREETING -> {
                val msg = when (currentLang) {
                    AppLanguage.TAMIL -> "வணக்கம். நான் உங்களுக்கு எப்படி உதவ முடியும்?"
                    AppLanguage.HINDI -> "नमस्ते। मैं आपकी क्या मदद कर सकता हूँ?"
                    AppLanguage.TELUGU -> "నమస్కారం. నేను మీకు ఎలా సహాయం చేయగలను?"
                    AppLanguage.MALAYALAM -> "നമസ്കാരം. എനിക്ക് എങ്ങനെ നിങ്ങളെ സഹായിക്കാൻ കഴിയും?"
                    else -> "Hello. How can I help you today?"
                }
                VoiceActionResult.SpeakResponse(msg)
            }
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
            CommandIntent.CANCEL_SOS -> VoiceActionResult.CancelSos
            CommandIntent.EMERGENCY, CommandIntent.CALL_EMERGENCY -> VoiceActionResult.TriggerSos
            CommandIntent.FIND_CANE -> VoiceActionResult.TriggerFindCane
            CommandIntent.CHANGE_LANGUAGE -> {
                val langCode = command.parameters["language"] ?: "en"
                val newLang = when (langCode) {
                    "ta" -> AppLanguage.TAMIL
                    "hi" -> AppLanguage.HINDI
                    "te" -> AppLanguage.TELUGU
                    "ml" -> AppLanguage.MALAYALAM
                    else -> AppLanguage.ENGLISH
                }
                languageManager.setLanguage(newLang)
                VoiceActionResult.ChangeLanguage(newLang)
            }
            CommandIntent.BATTERY_STATUS -> {
                val msg = when (currentLang) {
                    AppLanguage.ENGLISH -> "Checking cane battery."
                    AppLanguage.TAMIL -> "பிரம்பு பேட்டரி சரிபார்க்கப்படுகிறது."
                    else -> "Checking battery status."
                }
                VoiceActionResult.SpeakResponse(msg)
            }
            else -> VoiceActionResult.UnknownCommand
        }
    }
}
