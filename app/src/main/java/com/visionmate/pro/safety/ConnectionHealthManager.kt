package com.visionmate.pro.safety

import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.model.ConnectionState

class ConnectionHealthManager {

    fun isBluetoothHealthy(state: ConnectionState): Boolean {
        return state.bluetoothStatus == ConnectionStatus.CONNECTED
    }

    fun isStreamHealthy(state: ConnectionState): Boolean {
        return state.wifiStreamStatus == ConnectionStatus.CONNECTED
    }
}
