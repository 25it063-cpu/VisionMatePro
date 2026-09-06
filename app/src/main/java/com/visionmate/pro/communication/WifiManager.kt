package com.visionmate.pro.communication

import com.visionmate.pro.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiManager {

    private val _streamStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    val streamStatus: StateFlow<ConnectionStatus> = _streamStatus.asStateFlow()

    private val _streamUrl = MutableStateFlow("http://192.168.0.9:81/stream")
    val streamUrl: StateFlow<String> = _streamUrl.asStateFlow()

    fun updateStreamUrl(url: String) {
        _streamUrl.value = url
    }

    fun setStatus(status: ConnectionStatus) {
        _streamStatus.value = status
    }
}
