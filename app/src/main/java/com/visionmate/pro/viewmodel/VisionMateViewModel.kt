package com.visionmate.pro.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionmate.pro.BuildConfig
import com.visionmate.pro.ai.DecisionEngine
import com.visionmate.pro.ai.YoloObjectDetector
import com.visionmate.pro.ai.ObjectTracker
import com.visionmate.pro.ai.SceneAnalyzer
import com.visionmate.pro.ai.GeminiManager
import com.visionmate.pro.ai.GeminiVisionAnalyzer
import com.visionmate.pro.communication.ESP32CommandSender
import com.visionmate.pro.communication.RealBluetoothManager
import com.visionmate.pro.communication.RealCameraStreamReceiver
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
import com.visionmate.pro.navigation.MapsManager
import com.visionmate.pro.safety.AlertCooldownManager
import com.visionmate.pro.safety.ProximityStateManager
import com.visionmate.pro.safety.SafetyManager
import com.visionmate.pro.safety.SystemHealthMonitor
import com.visionmate.pro.vibration.HapticManager
import com.visionmate.pro.intent.IntentClassifier
import com.visionmate.pro.intent.LanguageDetector
import com.visionmate.pro.voice.SpeechRecognizerManager
import com.visionmate.pro.voice.TextToSpeechManager
import com.visionmate.pro.voice.VoiceActionResult
import com.visionmate.pro.voice.VoiceCommandProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VisionMateViewModel(application: Application) : AndroidViewModel(application) {

    val preferencesRepository = PreferencesRepository(application)
    val bluetoothManager = RealBluetoothManager(application)
    val cameraStreamReceiver = RealCameraStreamReceiver()
    val esp32CommandSender = ESP32CommandSender(bluetoothManager)
    private val geminiVisionAnalyzer = GeminiVisionAnalyzer()

    val proximityStateManager = ProximityStateManager()
    val speechRecognizerManager = SpeechRecognizerManager(application)
    val languageManager = LanguageManager()
    val voiceCommandProcessor = VoiceCommandProcessor(languageManager)
    private val languageDetector = LanguageDetector()
    private val geminiManager = GeminiManager()
    val mapsManager = MapsManager(application)

    // YOLO Object Detector and Coordination logic
    val objectDetector = YoloObjectDetector(application)
    private val objectTracker = ObjectTracker()
    private val alertCooldownManager = AlertCooldownManager()
    private val responseTemplateManager = ResponseTemplateManager()
    private val decisionEngine = DecisionEngine(
        proximityStateManager,
        alertCooldownManager,
        objectTracker,
        responseTemplateManager
    )

    val contactManager = ContactManager()
    val hapticManager = HapticManager(application)
    val ttsManager = TextToSpeechManager(application)

    val emergencyModeManager = EmergencyModeManager(
        application, LocationServicesManager(application), SMSManager(application),
        contactManager, hapticManager, ttsManager
    )

    private val _caneState = MutableStateFlow(CaneState())
    val caneState: StateFlow<CaneState> = _caneState.asStateFlow()

    private val _assistantState = MutableStateFlow(AssistantState.SAFE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _proximityState = MutableStateFlow(ProximityState.SAFE)
    val proximityState: StateFlow<ProximityState> = _proximityState.asStateFlow()

    private val _currentAlertText = MutableStateFlow("STARTING...")
    val currentAlertText: StateFlow<String> = _currentAlertText.asStateFlow()

    val currentLanguage: StateFlow<AppLanguage> = languageManager.currentLanguage
    val systemHealthMonitor = SystemHealthMonitor()
    val systemHealth: StateFlow<SystemHealth> = systemHealthMonitor.systemHealth
    val emergencyState: StateFlow<EmergencyState> = emergencyModeManager.emergencyState

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    init {
        val savedPrefs = preferencesRepository.preferences.value
        contactManager.updateContact(savedPrefs.emergencyContactName, savedPrefs.emergencyContactPhone)
        languageManager.setLanguageByCode(savedPrefs.languageCode)
        
        observeTelemetryAndRunPipeline()
        startVisionLoop()
        startInferenceLoop() // Passes every decoded frame to YOLO
        refreshPairedDevices()
        
        // INDEPENDENT STARTUP: Don't wait for one to start the other
        cameraStreamReceiver.startStreaming(RealCameraStreamReceiver.STREAM_URL)
        
        viewModelScope.launch {
            delay(2000) // Small offset to prevent radio antenna contention
            autoConnectCane()
        }
    }

    /**
     * Continuous YOLOv8 inference loop.
     * Passes every decoded latest frame into the model for inference.
     */
    private fun startInferenceLoop() {
        viewModelScope.launch {
            cameraStreamReceiver.latestFrame.collect { frame ->
                if (frame != null && bluetoothManager.connectionStatus.value == ConnectionStatus.CONNECTED) {
                    // 1. Run YOLO inference on the frame
                    val rawDetections = objectDetector.processFrame(frame)
                    
                    // 2. Forward results to coordination logic (DecisionEngine)
                    val sensorData = bluetoothManager.sensorDataFlow.value
                    val health = systemHealthMonitor.systemHealth.value
                    val lang = languageManager.currentLanguage.value
                    
                    val alert = decisionEngine.processFrameAndSensors(rawDetections, sensorData, health, lang)
                    
                    // 3. Feed detections into TTS announcement system
                    alert?.let {
                        // Announces: "[Object] detected at [distance] centimeters"
                        ttsManager.speak(it.speechText, lang, flush = it.proximityState == ProximityState.TOO_NEAR)
                        updateAlertText(it.displayText, true)
                        
                        if (it.proximityState == ProximityState.TOO_NEAR) hapticManager.vibrateAlert()
                        else hapticManager.vibrateCaution()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        _pairedDevices.value = bluetoothManager.getPairedDevices()
    }

    fun connectToDevice(address: String) {
        bluetoothManager.connect(address)
    }

    @SuppressLint("MissingPermission")
    private fun autoConnectCane() {
        viewModelScope.launch {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
            if (!adapter.isEnabled) return@launch
            val pairedDevices = adapter.bondedDevices?.toList() ?: return@launch
            val caneDevice = pairedDevices.find { device ->
                device.name?.contains("VisionMate", ignoreCase = true) == true || 
                device.name?.contains("ESP32", ignoreCase = true) == true 
            }
            caneDevice?.let { bluetoothManager.connect(it.address) }
        }
    }

    private var lastFrontAlertTimeMs = 0L
    private var lastVisionAlertMs = 0L
    private var uiAlertLockMs = 0L
    private var lastAlertState: ProximityState = ProximityState.SAFE

    private fun startVisionLoop() {
        viewModelScope.launch {
            while (true) {
                delay(2000)
                try {
                    val frame = cameraStreamReceiver.latestFrame.value ?: continue
                    val bitmap = frame.bitmap ?: continue
                    val lang = languageManager.currentLanguage.value

                    val result = geminiVisionAnalyzer.analyzeFrame(bitmap, lang)
                    if (result != null) {
                        val now = System.currentTimeMillis()
                        if ((now - lastVisionAlertMs) > 4000L) {
                            lastVisionAlertMs = now
                            hapticManager.vibrateCaution()
                            ttsManager.speak(result, lang, flush = false)
                            updateAlertText("📷 $result", true)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VisionLoop", "Error: ${e.message}")
                }
            }
        }
    }

    private fun observeTelemetryAndRunPipeline() {
        viewModelScope.launch {
            while (true) {
                delay(100)
                val connStatus = bluetoothManager.connectionStatus.value
                val rawData = bluetoothManager.sensorDataFlow.value
                val isSensorsActive = connStatus == ConnectionStatus.CONNECTED

                systemHealthMonitor.updateHealth(
                    _caneState.value.connectionState, 
                    cameraStreamReceiver.isFrameLive.value, 
                    BuildConfig.GEMINI_API_KEY.isNotEmpty(), 
                    isSensorsActive, true
                )

                if (!isSensorsActive) {
                    _currentAlertText.value = "CANE DISCONNECTED"
                    continue
                }

                _caneState.value = CaneState(
                    connectionState = _caneState.value.connectionState.copy(bluetoothStatus = connStatus),
                    sensorData = rawData,
                    batteryState = if (rawData.caneBatteryPercent < 20) BatteryLevelState.LOW else BatteryLevelState.NORMAL
                )
                
                // Manual sensor alerts removed: sensors are now used ONLY for distance measurement inside startInferenceLoop
                val now = System.currentTimeMillis()
                if (proximityStateManager.calculateProximityState(rawData.frontDistanceCm) == ProximityState.SAFE) {
                    if ((now - uiAlertLockMs) > 2000L) _currentAlertText.value = "CONNECTED"
                }
            }
        }
    }

    private fun updateAlertText(text: String, isSticky: Boolean) {
        _currentAlertText.value = text
        if (isSticky) uiAlertLockMs = System.currentTimeMillis()
    }

    fun onMicClicked() {
        if (speechRecognizerManager.isListening.value) {
            speechRecognizerManager.stopListening()
        } else {
            val currentLang = languageManager.currentLanguage.value
            speechRecognizerManager.startListening(currentLang) { candidates ->
                viewModelScope.launch {
                    val quickAction = IntentClassifier.classify(candidates[0])
                    val result = voiceCommandProcessor.processCommand(quickAction)
                    if (result !is VoiceActionResult.UnknownCommand) handleVoiceActionResult(result)
                    else {
                        val geminiCommand = geminiManager.classifyIntent(candidates)
                        handleVoiceActionResult(voiceCommandProcessor.processCommand(geminiCommand))
                    }
                }
            }
        }
    }

    private fun handleVoiceActionResult(result: VoiceActionResult) {
        val lang = languageManager.currentLanguage.value
        when (result) {
            is VoiceActionResult.SpeakResponse -> ttsManager.speak(result.message, lang)
            is VoiceActionResult.TriggerFindCane -> esp32CommandSender.sendFindMyCane()
            is VoiceActionResult.TriggerSos -> emergencyModeManager.triggerEmergency()
            is VoiceActionResult.CancelSos -> emergencyModeManager.cancelEmergency()
            is VoiceActionResult.ChangeLanguage -> {
                setLanguage(result.language)
                ttsManager.speak("Language changed.", result.language)
            }
            is VoiceActionResult.StartNavigation -> mapsManager.openGoogleMapsNavigation(result.destination)
            else -> {}
        }
    }

    fun setLanguage(language: AppLanguage) {
        languageManager.setLanguage(language)
        preferencesRepository.updateLanguage(language)
    }

    fun updateEmergencyContact(name: String, phone: String) {
        contactManager.updateContact(name, phone)
        preferencesRepository.updateEmergencyContact(name, phone)
    }

    fun toggleFindMyCane() = esp32CommandSender.sendFindMyCane()
    fun onSosClicked() = emergencyModeManager.triggerEmergency()

    override fun onCleared() {
        super.onCleared()
        speechRecognizerManager.destroy()
        ttsManager.shutdown()
        bluetoothManager.disconnect()
    }
}
