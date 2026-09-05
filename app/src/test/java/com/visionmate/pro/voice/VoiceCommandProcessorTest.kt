package com.visionmate.pro.voice

import com.visionmate.pro.language.LanguageManager
import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.CommandIntent
import com.visionmate.pro.model.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceCommandProcessorTest {

    private lateinit var languageManager: LanguageManager
    private lateinit var processor: VoiceCommandProcessor

    @Before
    fun setup() {
        languageManager = LanguageManager()
        processor = VoiceCommandProcessor(languageManager)
    }

    @Test
    fun `processCommand NAVIGATE in English returns StartNavigation`() {
        languageManager.setLanguage(AppLanguage.ENGLISH)
        val command = VoiceCommand(
            rawText = "navigate to park",
            intent = CommandIntent.NAVIGATE,
            parameters = mapOf("destination" to "park")
        )

        val result = processor.processCommand(command)

        assertTrue(result is VoiceActionResult.StartNavigation)
        assertEquals("park", (result as VoiceActionResult.StartNavigation).destination)
    }

    @Test
    fun `processCommand NAVIGATE in Tamil returns StartNavigation`() {
        languageManager.setLanguage(AppLanguage.TAMIL)
        val command = VoiceCommand(
            rawText = "பூங்காவிற்கு வழி காட்டு",
            intent = CommandIntent.NAVIGATE,
            parameters = mapOf("destination" to "பூங்கா")
        )

        val result = processor.processCommand(command)

        assertTrue(result is VoiceActionResult.StartNavigation)
        assertEquals("பூங்கா", (result as VoiceActionResult.StartNavigation).destination)
    }
    
    @Test
    fun `processCommand CHANGE_LANGUAGE updates manager and returns ChangeLanguage`() {
        val command = VoiceCommand(
            rawText = "தமிழ்",
            intent = CommandIntent.CHANGE_LANGUAGE
        )

        val result = processor.processCommand(command)

        assertTrue(result is VoiceActionResult.ChangeLanguage)
        assertEquals(AppLanguage.TAMIL, (result as VoiceActionResult.ChangeLanguage).language)
        assertEquals(AppLanguage.TAMIL, languageManager.currentLanguage.value)
    }
}
