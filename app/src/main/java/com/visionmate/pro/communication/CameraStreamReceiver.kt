package com.visionmate.pro.communication

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraFrame(
    val frameId: Long,
    val width: Int = 640,
    val height: Int = 480,
    val timestamp: Long = System.currentTimeMillis()
)

interface CameraStreamReceiver {
    val isStreaming: StateFlow<Boolean>
    val latestFrame: StateFlow<CameraFrame?>

    fun startStreaming(url: String)
    fun stopStreaming()
}

class MockCameraStreamReceiver : CameraStreamReceiver {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isStreaming = MutableStateFlow(true)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _latestFrame = MutableStateFlow<CameraFrame?>(null)
    override val latestFrame: StateFlow<CameraFrame?> = _latestFrame.asStateFlow()

    private var frameCounter = 0L

    init {
        startMockFrameLoop()
    }

    override fun startStreaming(url: String) {
        _isStreaming.value = true
    }

    override fun stopStreaming() {
        _isStreaming.value = false
    }

    private fun startMockFrameLoop() {
        scope.launch {
            while (true) {
                delay(100) // 10 FPS mock stream
                if (_isStreaming.value) {
                    frameCounter++
                    _latestFrame.value = CameraFrame(
                        frameId = frameCounter,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }
}
