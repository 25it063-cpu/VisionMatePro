package com.visionmate.pro.emergency

import com.visionmate.pro.communication.BluetoothCaneManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SOSManager(
    private val emergencyModeManager: EmergencyModeManager,
    private val bluetoothManager: BluetoothCaneManager
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        scope.launch {
            bluetoothManager.sosSignalEvents.collect {
                triggerPhysicalSos()
            }
        }
    }

    fun triggerPhysicalSos() {
        emergencyModeManager.triggerEmergency()
    }

    fun triggerVoiceSos() {
        emergencyModeManager.triggerEmergency()
    }

    fun cancelSos() {
        emergencyModeManager.cancelEmergency()
    }
}
