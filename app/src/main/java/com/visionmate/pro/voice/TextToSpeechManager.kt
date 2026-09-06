package com.visionmate.pro.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.visionmate.pro.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
                override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
                override fun onError(utteranceId: String?) { _isSpeaking.value = false }
            })
        }
    }

    /**
     * Speaks the given text loudly and clearly.
     * @param urgent If true, speaks faster and at a higher pitch for danger.
     */
    fun speak(text: String, language: AppLanguage, flush: Boolean = true, urgent: Boolean = false) {
        if (!isInitialized || text.trim().isEmpty()) return

        val locale = Locale.forLanguageTag(language.ttsLocaleTag)
        tts?.setLanguage(locale)
        
        if (urgent) {
            tts?.setPitch(1.3f)
            tts?.setSpeechRate(1.2f)
        } else {
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
        }

        // Force 100% volume for safety alerts
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "alert_${System.currentTimeMillis()}"
        
        tts?.speak(text, queueMode, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
