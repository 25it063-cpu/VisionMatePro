package com.visionmate.pro.communication

class ESP32CommandSender(
    private val bluetoothManager: BluetoothManager
) {

    fun sendFindMyCane() {
        bluetoothManager.sendCommand("CMD:BUZZER:1")
    }

    fun stopFindMyCane() {
        bluetoothManager.sendCommand("CMD:BUZZER:0")
    }

    fun sendUpdateRate(rateMs: Int) {
        bluetoothManager.sendCommand("CMD:RATE:$rateMs")
    }

    fun triggerPhysicalSosReset() {
        bluetoothManager.sendCommand("CMD:SOS_RESET:1")
    }
}
