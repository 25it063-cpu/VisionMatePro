package com.visionmate.pro.emergency

import android.content.Context
import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.EmergencyState
import com.visionmate.pro.model.EmergencyStatus
import com.visionmate.pro.vibration.HapticManager
import com.visionmate.pro.voice.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmergencyModeManager(
    private val context: Context,
    private val locationManager: LocationServicesManager,
    private val smsManager: SMSManager,
    private val contactManager: ContactManager,
    private val hapticManager: HapticManager,
    private val ttsManager: TextToSpeechManager
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null
    private val phoneCallManager = PhoneCallManager(context)

    private val _emergencyState = MutableStateFlow(EmergencyState())
    val emergencyState: StateFlow<EmergencyState> = _emergencyState.asStateFlow()

    fun triggerEmergency(customMessage: String? = null, language: AppLanguage = AppLanguage.ENGLISH) {
        if (_emergencyState.value.status != EmergencyStatus.IDLE) return

        val initialMsg = customMessage ?: "Emergency alert ready. Countdown started. Say cancel to stop."
        
        _emergencyState.value = EmergencyState(
            status = EmergencyStatus.PREPARING,
            message = initialMsg
        )

        hapticManager.vibrateEmergency()
        ttsManager.speak(initialMsg, language)

        scope.launch {
            val contact = contactManager.contact.value
            val loc = locationManager.getCurrentLocation()

            val lat = loc?.first ?: 13.0827
            val lng = loc?.second ?: 80.2707

            _emergencyState.value = EmergencyState(
                status = EmergencyStatus.AWAITING_CONFIRMATION,
                emergencyContactName = contact.name,
                emergencyContactNumber = contact.phoneNumber,
                latitude = lat,
                longitude = lng,
                remainingSeconds = 10,
                message = initialMsg
            )

            startConfirmationCountdown(language)
        }
    }

    private fun startConfirmationCountdown(language: AppLanguage) {
        timerJob?.cancel()
        timerJob = scope.launch {
            for (sec in 10 downTo 1) {
                if (_emergencyState.value.status != EmergencyStatus.AWAITING_CONFIRMATION) return@launch

                _emergencyState.value = _emergencyState.value.copy(remainingSeconds = sec)
                hapticManager.vibrateCaution()
                
                // Optional: Voice countdown for last 3 seconds
                if (sec <= 3) {
                    ttsManager.speak(sec.toString(), language)
                }
                
                delay(1000)
            }

            if (_emergencyState.value.status == EmergencyStatus.AWAITING_CONFIRMATION) {
                dispatchEmergencyAlert(language)
            }
        }
    }

    fun cancelEmergency() {
        timerJob?.cancel()
        _emergencyState.value = EmergencyState(
            status = EmergencyStatus.CANCELLED,
            message = "Emergency alert cancelled."
        )
        ttsManager.speak("Emergency cancelled.", AppLanguage.ENGLISH)

        scope.launch {
            delay(3000)
            resetToIdle()
        }
    }

    private fun dispatchEmergencyAlert(language: AppLanguage) {
        val state = _emergencyState.value
        val sendingMsg = "Sending alert and calling guardian..."
        
        _emergencyState.value = state.copy(
            status = EmergencyStatus.SENDING,
            message = sendingMsg
        )
        ttsManager.speak(sendingMsg, language)

        hapticManager.vibrateEmergency()

        val smsSuccess = smsManager.sendEmergencySms(
            recipientNumber = state.emergencyContactNumber,
            latitude = state.latitude ?: 13.0827,
            longitude = state.longitude ?: 80.2707
        )

        phoneCallManager.makeEmergencyCall(state.emergencyContactNumber)

        if (smsSuccess) {
            _emergencyState.value = _emergencyState.value.copy(
                status = EmergencyStatus.SENT,
                message = "Alert sent successfully."
            )
        } else {
            _emergencyState.value = _emergencyState.value.copy(
                status = EmergencyStatus.FAILED,
                message = "SMS failed, but call initiated."
            )
        }

        scope.launch {
            delay(5000)
            resetToIdle()
        }
    }

    fun resetToIdle() {
        timerJob?.cancel()
        _emergencyState.value = EmergencyState(status = EmergencyStatus.IDLE)
    }
}
