package com.visionmate.pro.repository

import com.visionmate.pro.ai.ObjectDetector
import com.visionmate.pro.communication.BluetoothManager
import com.visionmate.pro.communication.CameraStreamReceiver
import com.visionmate.pro.communication.ESP32CommandSender
import com.visionmate.pro.data.PreferencesRepository
import com.visionmate.pro.emergency.EmergencyModeManager
import com.visionmate.pro.emergency.SOSManager
import com.visionmate.pro.language.LanguageManager
import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.model.Obstacle
import com.visionmate.pro.model.SensorData
import com.visionmate.pro.safety.SystemHealthMonitor
import kotlinx.coroutines.flow.StateFlow

class VisionMateRepository(
    val bluetoothManager: BluetoothManager,
    val cameraStreamReceiver: CameraStreamReceiver,
    val objectDetector: ObjectDetector,
    val esp32CommandSender: ESP32CommandSender,
    val systemHealthMonitor: SystemHealthMonitor,
    val emergencyModeManager: EmergencyModeManager,
    val sosManager: SOSManager,
    val languageManager: LanguageManager,
    val preferencesRepository: PreferencesRepository
) {

    val connectionStatus: StateFlow<ConnectionStatus> = bluetoothManager.connectionStatus
    val sensorData: StateFlow<SensorData> = bluetoothManager.sensorDataFlow
    val detectedObstacles: StateFlow<List<Obstacle>> = objectDetector.detectedObstacles

    fun triggerFindMyCane() {
        esp32CommandSender.sendFindMyCane()
    }

    fun stopFindMyCane() {
        esp32CommandSender.stopFindMyCane()
    }
}
