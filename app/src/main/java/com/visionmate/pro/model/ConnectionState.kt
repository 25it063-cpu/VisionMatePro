package com.visionmate.pro.model

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}

data class ConnectionState(
    val bluetoothStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val wifiStreamStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val caneDeviceName: String = "ESP32-SmartCane-Pro",
    val cameraIpAddress: String = "192.168.4.1",
    val lastHeartbeatTime: Long = 0L
)
