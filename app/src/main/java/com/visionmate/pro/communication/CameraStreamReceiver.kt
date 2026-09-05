package com.visionmate.pro.communication

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraFrame(
    val frameId: Long,
    val bitmap: Bitmap? = null,
    val width: Int = 640,
    val height: Int = 480,
    val timestamp: Long = System.currentTimeMillis()
)

interface CameraStreamReceiver {
    val isStreaming: StateFlow<Boolean>
    val latestFrame: StateFlow<CameraFrame?>
    val isFrameLive: StateFlow<Boolean>

    fun startStreaming(url: String)
    fun stopStreaming()
}

class MockCameraStreamReceiver : CameraStreamReceiver {
    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming = _isStreaming.asStateFlow()

    private val _latestFrame = MutableStateFlow<CameraFrame?>(null)
    override val latestFrame = _latestFrame.asStateFlow()

    private val _isFrameLive = MutableStateFlow(false)
    override val isFrameLive = _isFrameLive.asStateFlow()

    override fun startStreaming(url: String) { _isStreaming.value = true }
    override fun stopStreaming() { _isStreaming.value = false }
}
