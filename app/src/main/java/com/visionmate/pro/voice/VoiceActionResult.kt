package com.visionmate.pro.voice

import com.visionmate.pro.model.AppLanguage

sealed class VoiceActionResult {
    data class SpeakResponse(val message: String) : VoiceActionResult()
    object TriggerFindCane : VoiceActionResult()
    object TriggerSos : VoiceActionResult()
    object CancelSos : VoiceActionResult()
    data class QueryScene(val text: String) : VoiceActionResult()
    data class ChangeLanguage(val language: AppLanguage) : VoiceActionResult()
    data class UpdateSosNumber(val number: String) : VoiceActionResult()
    data class StartNavigation(val destination: String) : VoiceActionResult()
    object UnknownCommand : VoiceActionResult()
}
