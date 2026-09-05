package com.visionmate.pro.model

data class SubsystemStatus(
    val name: String,
    val isReady: Boolean,
    val detail: String = ""
)

data class SystemHealth(
    val bluetooth: SubsystemStatus = SubsystemStatus("Bluetooth", false, "Disconnected"),
    val esp32: SubsystemStatus = SubsystemStatus("ESP32 Core", false, "Offline"),
    val esp32Cam: SubsystemStatus = SubsystemStatus("ESP32-CAM", false, "No Feed"),
    val cameraStream: SubsystemStatus = SubsystemStatus("Camera Stream", false, "Waiting..."),
    val aiVision: SubsystemStatus = SubsystemStatus("AI Vision Engine", false, "Initializing..."),
    val ultrasonicSensors: SubsystemStatus = SubsystemStatus("Ultrasonic Array", false, "Offline"),
    val batteryTelemetry: SubsystemStatus = SubsystemStatus("Battery Telemetry", false, "No Data"),
    val sosCommunication: SubsystemStatus = SubsystemStatus("SOS Link", false, "Disconnected")
) {
    val isFullyOperational: Boolean
        get() = bluetooth.isReady && esp32.isReady && esp32Cam.isReady &&
                cameraStream.isReady && aiVision.isReady && ultrasonicSensors.isReady &&
                batteryTelemetry.isReady && sosCommunication.isReady
}
