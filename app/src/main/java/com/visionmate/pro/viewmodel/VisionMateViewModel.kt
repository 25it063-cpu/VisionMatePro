package com.visionmate.pro.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionmate.pro.BuildConfig
import com.visionmate.pro.ai.*
import com.visionmate.pro.communication.*
import com.visionmate.pro.data.PreferencesRepository
import com.visionmate.pro.emergency.*
import com.visionmate.pro.language.LanguageManager
import com.visionmate.pro.language.ResponseTemplateManager
import com.visionmate.pro.model.*
import com.visionmate.pro.navigation.MapsManager
import com.visionmate.pro.safety.*
import com.visionmate.pro.vibration.HapticManager
import com.visionmate.pro.intent.IntentClassifier
import com.visionmate.pro.voice.SpeechRecognizerManager
import com.visionmate.pro.voice.TextToSpeechManager
import com.visionmate.pro.voice.VoiceActionResult
import com.visionmate.pro.voice.VoiceCommandProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VisionMateViewModel(application: Application) : AndroidViewModel(application) {

    val preferencesRepository = PreferencesRepository(application)
    val bluetoothManager = RealBluetoothManager(application)
    val cameraStreamReceiver = RealCameraStreamReceiver()
    val esp32CommandSender = ESP32CommandSender(bluetoothManager)
    
    // Safety & AI Integration (Requirement 1 & 4)
    val objectDetector = YoloObjectDetector(application)
    val proximityStateManager = ProximityStateManager()
    val objectTracker = ObjectTracker()
    private val alertCooldownManager = AlertCooldownManager(cooldownWindowMs = 2500L)
    private val responseTemplateManager = ResponseTemplateManager()
    private val decisionEngine = DecisionEngine(
        proximityStateManager, 
        alertCooldownManager, 
        objectTracker, 
        responseTemplateManager
    )

    val speechRecognizerManager = SpeechRecognizerManager(application)
    val languageManager = LanguageManager()
    val voiceCommandProcessor = VoiceCommandProcessor(languageManager)
    private val geminiManager = GeminiManager()
    val mapsManager = MapsManager(application)

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

    private val _currentAlertText = MutableStateFlow("INITIALIZING...")
    val currentAlertText: StateFlow<String> = _currentAlertText.asStateFlow()

    val currentLanguage: StateFlow<AppLanguage> = languageManager.currentLanguage
    val systemHealthMonitor = SystemHealthMonitor()
    val systemHealth: StateFlow<SystemHealth> = systemHealthMonitor.systemHealth
    val emergencyState: StateFlow<EmergencyState> = emergencyModeManager.emergencyState

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private var uiAlertLockMs = 0L
    private var inferenceJob: Job? = null
    private var sensorJob: Job? = null

    private val lastVisualSectorSeenMs = java.util.concurrent.ConcurrentHashMap<Direction, Long>()
    private val SECTOR_VISUAL_TTL_MS = 600L
    private val FUSION_GRACE_WINDOW_MS = 120L
    private val pendingSectorJobs = java.util.concurrent.ConcurrentHashMap<Direction, Job>()
    private val pendingSectorData = java.util.concurrent.ConcurrentHashMap<Direction, SensorData>()

    private fun getOccupiedVisualSectors(): Set<Direction> {
        val now = System.currentTimeMillis()
        return lastVisualSectorSeenMs.entries
            .filter { (now - it.value) <= SECTOR_VISUAL_TTL_MS }
            .map { it.key }
            .toSet()
    }

    fun updateCameraStreamUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) {
            preferencesRepository.updateCameraUrl(trimmed)
            reconnectCamera()
        }
    }

    fun reconnectCamera() {
        val currentUrl = preferencesRepository.preferences.value.cameraStreamUrl
        cameraStreamReceiver.stopStreaming()
        cameraStreamReceiver.startStreaming(currentUrl)
    }

    init {
        val savedPrefs = preferencesRepository.preferences.value
        contactManager.updateContact(savedPrefs.emergencyContactName, savedPrefs.emergencyContactPhone)
        languageManager.setLanguageByCode(savedPrefs.languageCode)
        
        observeTelemetryAndRunPipeline()
        startInferenceLoop() 
        startSensorTelemetryLoop()
        refreshPairedDevices()
        
        // Independent Camera Startup
        viewModelScope.launch {
            _currentAlertText.value = "CONNECTING CAMERA..."
            cameraStreamReceiver.startStreaming(RealCameraStreamReceiver.STREAM_URL)
            
            // Wait for first frame to arrive (Confirmed Live)
            cameraStreamReceiver.isFrameLive.first { it }
            ttsManager.speak("Camera Ready.", languageManager.currentLanguage.value)
        }

        // Independent Bluetooth Auto-Connect (Zero dependency on camera stream or delay)
        viewModelScope.launch {
            autoConnectCane()
        }
    }

    /**
     * Continuous YOLO Inference using Latest-Frame-Only approach.
     * - Only one YOLO inference runs at a time.
     * - Intermediate / stale camera frames are automatically dropped via conflate().
     * - Always processes the newest available frame.
     * - Inference runs strictly off the main/UI thread on Dispatchers.Default.
     * - A recoverable exception does not permanently kill the inference loop.
     * - Single managed coroutine job prevents duplicate inference loops or coroutine backlogs.
     */
    private fun startInferenceLoop() {
        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            cameraStreamReceiver.latestFrame.collect { frame ->
                if (frame == null) return@collect
                try {
                    // 1. Run YOLO inference on background thread
                    val detections = objectDetector.processFrame(frame)
                    val now = System.currentTimeMillis()
                    for (det in detections) {
                        if (!det.isUpperObstacle && det.objectType != ObjectType.TREE_BRANCH) {
                            val sector = DirectionDetector.determineDirection(det.boundingBox)
                            lastVisualSectorSeenMs[sector] = now
                            // Cancel any pending standalone ultrasonic alert for this sector
                            pendingSectorJobs.remove(sector)?.cancel()
                        }
                    }

                    // 2. Coordinated Logic: Combine what YOLO sees with ultrasonic distance
                    val isBtConnected = bluetoothManager.connectionStatus.value == ConnectionStatus.CONNECTED
                    var rawSensorData = bluetoothManager.sensorDataFlow.value

                    if (isBtConnected && !rawSensorData.isFresh()) {
                        withTimeoutOrNull(100L) {
                            bluetoothManager.sensorDataFlow.first { it.isFresh() }
                        }?.let { freshData ->
                            rawSensorData = freshData
                        }
                    }

                    val sensorData = if (isBtConnected && rawSensorData.isFresh()) rawSensorData else SensorData.invalid()
                    val lang = languageManager.currentLanguage.value
                    val alert = decisionEngine.processFrameAndSensors(detections, sensorData, systemHealth.value, lang)

                    // 3. Announcements & UI dispatch on Main thread
                    if (alert != null) {
                        handleAlert(alert, lang)
                    } else {
                        withContext(Dispatchers.Main) {
                            if (_assistantState.value != AssistantState.LISTENING && 
                                _assistantState.value != AssistantState.SPEAKING && 
                                _assistantState.value != AssistantState.EMERGENCY) {
                                _assistantState.value = AssistantState.SAFE
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("VisionMateVM", "Recoverable error during YOLO inference: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Independent Bluetooth Sensor Telemetry Loop.
     * - Evaluates standalone ultrasonic and water hazards in real-time on Dispatchers.Default.
     * - Does NOT re-evaluate visual detections or mutate visual tracking state.
     * - Controlled Per-Sector Pending Fusion Grace Window (120ms):
     *   When a non-critical hazard appears in an unoccupied sector, waits up to 120ms for matching visual detection.
     *   If YOLO identifies an object in that sector, the pending generic alert is cancelled in favor of fused alert.
     *   Critical/TOO_NEAR hazards and disconnected camera bypass grace window for immediate warning.
     */
    private fun startSensorTelemetryLoop() {
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch(Dispatchers.Default) {
            bluetoothManager.sensorDataFlow.collect { rawData ->
                val connStatus = bluetoothManager.connectionStatus.value
                if (connStatus != ConnectionStatus.CONNECTED || !rawData.isFresh()) return@collect

                val isCameraLive = cameraStreamReceiver.isFrameLive.value
                val now = System.currentTimeMillis()
                val occupiedSectors = getOccupiedVisualSectors()

                // 1. Hardware water hazard evaluated immediately
                if (rawData.isWaterDetected || rawData.rawWaterValue > 500) {
                    val lang = languageManager.currentLanguage.value
                    val alert = decisionEngine.processStandaloneSensors(rawData, occupiedSectors, lang)
                    if (alert != null) handleAlert(alert, lang)
                    return@collect
                }

                // 2. Ultrasonic sectors evaluation
                val sectorReadings = listOf(
                    Triple(Direction.CENTER, rawData.frontDistanceCm, rawData.isFrontCritical),
                    Triple(Direction.LEFT, rawData.leftDistanceCm, rawData.isLeftCritical),
                    Triple(Direction.RIGHT, rawData.rightDistanceCm, rawData.isRightCritical)
                )

                for ((sector, distance, isCrit) in sectorReadings) {
                    if (distance < 0) {
                        pendingSectorJobs.remove(sector)?.cancel()
                        continue
                    }

                    val proxState = proximityStateManager.calculateProximityState(distance)
                    if (proxState == ProximityState.SAFE && !isCrit) {
                        pendingSectorJobs.remove(sector)?.cancel()
                        continue
                    }

                    // Sector is currently occupied by a recent visual detection
                    val isVisuallyOccupied = (now - (lastVisualSectorSeenMs[sector] ?: 0L)) <= SECTOR_VISUAL_TTL_MS
                    if (isVisuallyOccupied) {
                        pendingSectorJobs.remove(sector)?.cancel()
                        continue
                    }

                    // Critical / TOO_NEAR hazards or disconnected camera bypass grace window
                    val shouldBypassGrace = proxState == ProximityState.TOO_NEAR || isCrit || !isCameraLive
                    if (shouldBypassGrace) {
                        pendingSectorJobs.remove(sector)?.cancel()
                        val lang = languageManager.currentLanguage.value
                        val alert = decisionEngine.processStandaloneSensors(rawData, getOccupiedVisualSectors(), lang)
                        if (alert != null) handleAlert(alert, lang)
                        continue
                    }

                    // Non-critical hazard with live camera: update pending data and manage single pending job per sector
                    pendingSectorData[sector] = rawData
                    val existingJob = pendingSectorJobs[sector]
                    if (existingJob == null || !existingJob.isActive) {
                        val job = viewModelScope.launch(Dispatchers.Default) {
                            delay(FUSION_GRACE_WINDOW_MS)
                            pendingSectorJobs.remove(sector)

                            // Re-check: Did YOLO detect an object in this sector during the grace window?
                            val currentOccupied = getOccupiedVisualSectors()
                            if (!currentOccupied.contains(sector)) {
                                // Grace window expired with no matching visual object: Evaluate standalone alert
                                val latestData = pendingSectorData[sector] ?: rawData
                                val lang = languageManager.currentLanguage.value
                                val alert = decisionEngine.processStandaloneSensors(
                                    latestData,
                                    currentOccupied,
                                    lang
                                )
                                if (alert != null) {
                                    handleAlert(alert, lang)
                                }
                            }
                        }
                        pendingSectorJobs[sector] = job
                    }
                }
            }
        }
    }

    private suspend fun handleAlert(alert: ObstacleAlert, lang: AppLanguage) {
        withContext(Dispatchers.Main) {
            val shouldFlush = alert.proximityState == ProximityState.TOO_NEAR || alert.isDistanceUpgrade
            ttsManager.speak(
                alert.speechText, 
                lang, 
                flush = shouldFlush, 
                urgent = alert.proximityState == ProximityState.TOO_NEAR
            )
            updateAlertText(alert.displayText, true)
            _proximityState.value = alert.proximityState
            _assistantState.value = when (alert.proximityState) {
                ProximityState.TOO_NEAR -> AssistantState.TOO_NEAR
                ProximityState.NEAR -> AssistantState.NEAR
                else -> AssistantState.SAFE
            }
            if (alert.proximityState == ProximityState.TOO_NEAR) {
                hapticManager.vibrateAlert()
            } else {
                hapticManager.vibrateCaution()
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

                _assistantState.value = if (emergencyModeManager.emergencyState.value.status != com.visionmate.pro.model.EmergencyStatus.IDLE) {
                    AssistantState.EMERGENCY
                } else if (speechRecognizerManager.isListening.value) {
                    AssistantState.LISTENING
                } else if (ttsManager.isSpeaking.value) {
                    AssistantState.SPEAKING
                } else {
                    _assistantState.value
                }

                _caneState.value = CaneState(
                    connectionState = _caneState.value.connectionState.copy(bluetoothStatus = connStatus),
                    sensorData = if (isSensorsActive) rawData else SensorData.invalid(),
                    batteryState = if (isSensorsActive && rawData.caneBatteryPercent < 20) BatteryLevelState.LOW else BatteryLevelState.NORMAL
                )

                if (!isSensorsActive) {
                    if ((System.currentTimeMillis() % 5000) < 100) _currentAlertText.value = "CANE DISCONNECTED"
                    continue
                }
                
                // Clear UI if path is safe and no AI alerts are active
                if (rawData.frontDistanceCm >= 0 && proximityStateManager.calculateProximityState(rawData.frontDistanceCm) == ProximityState.SAFE) {
                    if ((System.currentTimeMillis() - uiAlertLockMs) > 2500L) {
                        _currentAlertText.value = "CONNECTED"
                    }
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
        inferenceJob?.cancel()
        sensorJob?.cancel()
        pendingSectorJobs.values.forEach { it.cancel() }
        pendingSectorJobs.clear()
        pendingSectorData.clear()
        speechRecognizerManager.destroy()
        ttsManager.shutdown()
        bluetoothManager.disconnect()
        cameraStreamReceiver.stopStreaming()
    }
}
