package com.visionmate.pro.model

data class SubsystemStatus(
    val name: String,
    val isReady: Boolean,
    val detail: String = ""
)

data class SystemHealth(
    val bluetooth: SubsystemStatus = SubsystemStatus("Bluetooth", true, "Connected"),
    val esp32: SubsystemStatus = SubsystemStatus("ESP32 Core", true, "Active"),
    val esp32Cam: SubsystemStatus = SubsystemStatus("ESP32-CAM", true, "Streaming"),
    val cameraStream: SubsystemStatus = SubsystemStatus("Camera Stream", true, "Frames OK"),
    val aiVision: SubsystemStatus = SubsystemStatus("AI Vision Engine", true, "Ready"),
    val ultrasonicSensors: SubsystemStatus = SubsystemStatus("Ultrasonic Array", true, "Tri-axis Active"),
    val batteryTelemetry: SubsystemStatus = SubsystemStatus("Battery Telemetry", true, "Receiving Data"),
    val sosCommunication: SubsystemStatus = SubsystemStatus("SOS Link", true, "Channel Standby")
) {
    val isFullyOperational: Boolean
        get() = bluetooth.isReady && esp32.isReady && esp32Cam.isReady &&
                cameraStream.isReady && aiVision.isReady && ultrasonicSensors.isReady &&
                batteryTelemetry.isReady && sosCommunication.isReady
}
