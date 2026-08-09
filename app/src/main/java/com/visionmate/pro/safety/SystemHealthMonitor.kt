package com.visionmate.pro.safety

import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.model.ConnectionState
import com.visionmate.pro.model.SubsystemStatus
import com.visionmate.pro.model.SystemHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemHealthMonitor {

    private val _systemHealth = MutableStateFlow(SystemHealth())
    val systemHealth: StateFlow<SystemHealth> = _systemHealth.asStateFlow()

    fun updateHealth(
        connectionState: ConnectionState,
        isStreamActive: Boolean,
        isAiActive: Boolean,
        isSensorsActive: Boolean,
        isBatteryOk: Boolean
    ) {
        val btOk = connectionState.bluetoothStatus == ConnectionStatus.CONNECTED

        val updated = SystemHealth(
            bluetooth = SubsystemStatus("Bluetooth", btOk, if (btOk) "Connected" else "Disconnected"),
            esp32 = SubsystemStatus("ESP32 Core", btOk, if (btOk) "Active" else "Offline"),
            esp32Cam = SubsystemStatus("ESP32-CAM", isStreamActive, if (isStreamActive) "Streaming" else "No Feed"),
            cameraStream = SubsystemStatus("Camera Stream", isStreamActive, if (isStreamActive) "Frames OK" else "Stream Interrupted"),
            aiVision = SubsystemStatus("AI Vision Engine", isAiActive, if (isAiActive) "Ready" else "Unavailable"),
            ultrasonicSensors = SubsystemStatus("Ultrasonic Array", isSensorsActive, if (isSensorsActive) "Tri-axis Active" else "Sensors Offline"),
            batteryTelemetry = SubsystemStatus("Battery Telemetry", isBatteryOk, if (isBatteryOk) "Normal" else "Low/Critical"),
            sosCommunication = SubsystemStatus("SOS Link", btOk, if (btOk) "Standby" else "Disconnected")
        )

        _systemHealth.value = updated
    }
}
