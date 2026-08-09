package com.visionmate.pro.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionmate.pro.ai.DecisionEngine
import com.visionmate.pro.ai.MockObjectDetector
import com.visionmate.pro.ai.ObjectTracker
import com.visionmate.pro.ai.SceneAnalyzer
import com.visionmate.pro.communication.ESP32CommandSender
import com.visionmate.pro.communication.RealBluetoothManager
import com.visionmate.pro.communication.MockCameraStreamReceiver
import com.visionmate.pro.communication.WifiManager
import com.visionmate.pro.data.PreferencesRepository
import com.visionmate.pro.emergency.ContactManager
import com.visionmate.pro.emergency.EmergencyModeManager
import com.visionmate.pro.emergency.LocationServicesManager
import com.visionmate.pro.emergency.SMSManager
import com.visionmate.pro.emergency.SOSManager
import com.visionmate.pro.language.LanguageManager
import com.visionmate.pro.language.ResponseTemplateManager
import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.AssistantState
import com.visionmate.pro.model.BatteryLevelState
import com.visionmate.pro.model.CaneState
import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.model.EmergencyState
import com.visionmate.pro.model.EmergencyStatus
import com.visionmate.pro.model.ObstacleAlert
import com.visionmate.pro.model.ProximityState
import com.visionmate.pro.model.SensorData
import com.visionmate.pro.model.SystemHealth
import com.visionmate.pro.navigation.ContextAwareNavigationManager
import com.visionmate.pro.navigation.MapsManager
import com.visionmate.pro.safety.AlertCooldownManager
import com.visionmate.pro.safety.ProximityStateManager
import com.visionmate.pro.safety.SafetyManager
import com.visionmate.pro.safety.SystemEvent
import com.visionmate.pro.safety.SystemHealthMonitor
import com.visionmate.pro.vibration.HapticManager
import com.visionmate.pro.voice.IntentClassifier
import com.visionmate.pro.voice.ResponseGenerator
import com.visionmate.pro.voice.SpeechRecognizerManager
import com.visionmate.pro.voice.TextToSpeechManager
import com.visionmate.pro.voice.VoiceActionResult
import com.visionmate.pro.voice.VoiceCommandProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisionMateViewModel(application: Application) : AndroidViewModel(application) {

    val preferencesRepository = PreferencesRepository(application)
    val bluetoothManager = RealBluetoothManager()
    val cameraStreamReceiver = MockCameraStreamReceiver()
    val objectDetector = MockObjectDetector()
    val esp32CommandSender = ESP32CommandSender(bluetoothManager)
    val wifiManager = WifiManager()
    val systemHealthMonitor = SystemHealthMonitor()

    val proximityStateManager = ProximityStateManager()
    val alertCooldownManager = AlertCooldownManager()
    val objectTracker = ObjectTracker()
    val responseTemplateManager = ResponseTemplateManager()
    val decisionEngine = DecisionEngine(proximityStateManager, alertCooldownManager, objectTracker, responseTemplateManager)
    val safetyManager = SafetyManager()
    val sceneAnalyzer = SceneAnalyzer()

    val hapticManager = HapticManager(application)
    val ttsManager = TextToSpeechManager(application)
    val speechRecognizerManager = SpeechRecognizerManager(application)
    val languageManager = LanguageManager()
    val voiceCommandProcessor = VoiceCommandProcessor(languageManager)
    val responseGenerator = ResponseGenerator()

    val locationManager = LocationServicesManager(application)
    val smsManager = SMSManager(application)
    val contactManager = ContactManager()
    val emergencyModeManager = EmergencyModeManager(application, locationManager, smsManager, contactManager, hapticManager, ttsManager)
    val sosManager = SOSManager(emergencyModeManager, bluetoothManager)

    val mapsManager = MapsManager(application)
    val contextAwareNavigationManager = ContextAwareNavigationManager()

    private val _caneState = MutableStateFlow(CaneState())
    val caneState: StateFlow<CaneState> = _caneState.asStateFlow()

    private val _assistantState = MutableStateFlow(AssistantState.SAFE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _proximityState = MutableStateFlow(ProximityState.SAFE)
    val proximityState: StateFlow<ProximityState> = _proximityState.asStateFlow()

    private val _currentAlertText = MutableStateFlow("CANE DISCONNECTED")
    val currentAlertText: StateFlow<String> = _currentAlertText.asStateFlow()

    val systemHealth: StateFlow<SystemHealth> = systemHealthMonitor.systemHealth
    val emergencyState: StateFlow<EmergencyState> = emergencyModeManager.emergencyState
    val currentLanguage: StateFlow<AppLanguage> = languageManager.currentLanguage

    private var currentObstacleAlert: ObstacleAlert? = null

    init {
        val savedPrefs = preferencesRepository.preferences.value
        contactManager.updateContact(savedPrefs.emergencyContactName, savedPrefs.emergencyContactPhone)
        observeTelemetryAndRunPipeline()
    }

    private fun observeTelemetryAndRunPipeline() {
        viewModelScope.launch {
            while (true) {
                delay(100)
                val connStatus = bluetoothManager.connectionStatus.value
                val rawSensorData = bluetoothManager.sensorDataFlow.value
                val streamActive = cameraStreamReceiver.isStreaming.value
                val frame = cameraStreamReceiver.latestFrame.value

                val rawDetections = if (connStatus == ConnectionStatus.CONNECTED && frame != null) {
                    objectDetector.processFrame(frame) 
                } else emptyList()

                val batState = if (connStatus == ConnectionStatus.CONNECTED) {
                    when {
                        rawSensorData.caneBatteryPercent <= 15 -> BatteryLevelState.CRITICAL
                        rawSensorData.caneBatteryPercent <= 30 -> BatteryLevelState.LOW
                        else -> BatteryLevelState.NORMAL
                    }
                } else BatteryLevelState.NORMAL

                _caneState.value = CaneState(
                    connectionState = _caneState.value.connectionState.copy(bluetoothStatus = connStatus),
                    sensorData = if (connStatus == ConnectionStatus.CONNECTED) rawSensorData else SensorData(),
                    batteryState = batState
                )

                systemHealthMonitor.updateHealth(
                    connectionState = _caneState.value.connectionState,
                    isStreamActive = streamActive,
                    isAiActive = connStatus == ConnectionStatus.CONNECTED,
                    isSensorsActive = connStatus == ConnectionStatus.CONNECTED,
                    isBatteryOk = batState != BatteryLevelState.CRITICAL
                )

                val health = systemHealthMonitor.systemHealth.value
                val lang = languageManager.currentLanguage.value

                val newAlert = if (connStatus == ConnectionStatus.CONNECTED) {
                    decisionEngine.processFrameAndSensors(rawDetections, rawSensorData, health, lang)
                } else null
                
                if (newAlert != null) currentObstacleAlert = newAlert
                if (connStatus != ConnectionStatus.CONNECTED) currentObstacleAlert = null

                val calculatedProximity = proximityStateManager.calculateProximityState(
                    distanceCm = if (connStatus == ConnectionStatus.CONNECTED) rawSensorData.frontDistanceCm else 999,
                    isUpperObstacle = rawDetections.any { it.isUpperObstacle }
                )
                _proximityState.value = calculatedProximity

                val priorityEvent = safetyManager.evaluatePriorityEvent(
                    emergencyState = emergencyModeManager.emergencyState.value,
                    obstacleAlert = currentObstacleAlert,
                    systemHealth = health,
                    navigationPrompt = null,
                    normalAssistantResponse = null
                )

                handleSystemPriorityEvent(priorityEvent, calculatedProximity, lang)
            }
        }
    }

    private fun handleSystemPriorityEvent(event: SystemEvent?, proximity: ProximityState, language: AppLanguage) {
        val emState = emergencyModeManager.emergencyState.value
        if (emState.status != EmergencyStatus.IDLE) {
            _assistantState.value = AssistantState.EMERGENCY
            _currentAlertText.value = emState.message
            return
        }

        if (speechRecognizerManager.isListening.value) return

        if (ttsManager.isSpeaking.value) {
            _assistantState.value = AssistantState.SPEAKING
            return
        }

        if (_caneState.value.connectionState.bluetoothStatus != ConnectionStatus.CONNECTED) {
            _currentAlertText.value = "CANE DISCONNECTED"
            _assistantState.value = AssistantState.SAFE
            return
        }

        when (event) {
            is SystemEvent.ImmediateObstacle -> {
                _currentAlertText.value = event.alert.displayText
                ttsManager.speak(event.alert.speechText, language)
            }
            else -> {
                _currentAlertText.value = "PATH CLEAR"
            }
        }
    }

    fun onMicClicked() {
        if (speechRecognizerManager.isListening.value) {
            speechRecognizerManager.stopListening()
        } else {
            speechRecognizerManager.startListening(languageManager.currentLanguage.value) { rawSpeech ->
                val detectedLang = when {
                    rawSpeech.contains(Regex("[அ-ஹ]")) -> AppLanguage.TAMIL
                    rawSpeech.contains(Regex("[अ-ह]")) -> AppLanguage.HINDI
                    else -> AppLanguage.ENGLISH
                }
                
                if (detectedLang != languageManager.currentLanguage.value) {
                    languageManager.setLanguage(detectedLang)
                }

                val command = IntentClassifier.classify(rawSpeech)
                val actionResult = voiceCommandProcessor.processCommand(command)
                handleVoiceActionResult(actionResult)
            }
        }
    }

    private fun handleVoiceActionResult(result: VoiceActionResult) {
        val lang = languageManager.currentLanguage.value
        when (result) {
            is VoiceActionResult.SpeakResponse -> ttsManager.speak(result.message, lang)
            is VoiceActionResult.TriggerFindCane -> toggleFindMyCane()
            is VoiceActionResult.TriggerSos -> onSosClicked()
            is VoiceActionResult.CancelSos -> onCancelSosClicked()
            is VoiceActionResult.UpdateSosNumber -> {
                val newNumber = result.number
                updateEmergencyContact("Guardian", newNumber)
                
                val spokenNumber = newNumber.chunked(1).joinToString(", ")
                
                val confirmationMsg = when (lang) {
                    AppLanguage.TAMIL -> "அவசர எண் $spokenNumber என மாற்றப்பட்டது."
                    AppLanguage.HINDI -> "आपातकालीन नंबर $spokenNumber में बदल दिया गया है।"
                    else -> "SOS number updated to $spokenNumber."
                }
                
                _currentAlertText.value = "SOS: $newNumber"
                _assistantState.value = AssistantState.SPEAKING
                ttsManager.speak(confirmationMsg, lang)
            }
            is VoiceActionResult.StartNavigation -> {
                val destination = result.destination
                val msg = when (lang) {
                    AppLanguage.TAMIL -> "$destination க்கு வழிகாட்டுகிறேன்."
                    AppLanguage.HINDI -> "$destination के लिए नेविगेशन शुरू कर रहा हूँ।"
                    else -> "Starting navigation to $destination."
                }
                _currentAlertText.value = msg
                ttsManager.speak(msg, lang)
                mapsManager.openGoogleMapsNavigation(destination)
            }
            else -> {}
        }
    }

    fun updateEmergencyContact(name: String, phone: String) {
        contactManager.updateContact(name, phone)
        preferencesRepository.updateEmergencyContact(name, phone)
    }

    fun toggleFindMyCane() {
        esp32CommandSender.sendFindMyCane()
    }

    fun setLanguage(language: AppLanguage) {
        languageManager.setLanguage(language)
    }

    fun onSosClicked() {
        emergencyModeManager.triggerEmergency()
    }

    fun onCancelSosClicked() {
        emergencyModeManager.cancelEmergency()
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizerManager.destroy()
        ttsManager.shutdown()
        bluetoothManager.disconnect()
    }
}
