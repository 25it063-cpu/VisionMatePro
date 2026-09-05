package com.visionmate.pro.communication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class RealCameraStreamReceiver : CameraStreamReceiver {

    companion object {
        // IMPORTANT: Ensure this matches exactly what is in your browser!
        const val STREAM_URL = "http://10.112.154.28:81/stream"
        private const val TAG = "CameraStream"
        private const val BUFFER_SIZE = 16384 // 16KB high-speed buffer
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _latestFrame = MutableStateFlow<CameraFrame?>(null)
    override val latestFrame: StateFlow<CameraFrame?> = _latestFrame.asStateFlow()

    private val _isFrameLive = MutableStateFlow(false)
    override val isFrameLive: StateFlow<Boolean> = _isFrameLive.asStateFlow()

    private var frameCounter = 0L
    private var lastFrameTime = 0L

    init {
        monitorFrameHealth()
    }

    private fun monitorFrameHealth() {
        scope.launch {
            while (isActive) {
                delay(3000)
                _isFrameLive.value = (System.currentTimeMillis() - lastFrameTime) < 5000
            }
        }
    }

    override fun startStreaming(url: String) {
        if (_isStreaming.value) return
        Log.i(TAG, "Starting stream: $url")
        
        streamJob?.cancel()
        streamJob = scope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            _isStreaming.value = false
                            _isFrameLive.value = false
                            delay(3000)
                            return@use
                        }

                        _isStreaming.value = true
                        val inputStream = BufferedInputStream(response.body?.byteStream() ?: return@use)
                        val frameBuffer = ByteArrayOutputStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        
                        var prevByte = -1
                        while (isActive && _isStreaming.value) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead <= 0) break
                            
                            for (i in 0 until bytesRead) {
                                val b = buffer[i].toInt() and 0xFF
                                frameBuffer.write(b)

                                // Detect JPEG end marker (0xFF 0xD9)
                                if (prevByte == 0xFF && b == 0xD9) {
                                    processJpeg(frameBuffer.toByteArray())
                                    frameBuffer.reset()
                                }
                                prevByte = b
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Stream error: ${e.message}")
                    _isStreaming.value = false
                    _isFrameLive.value = false
                    delay(3000)
                }
            }
        }
    }

    private fun processJpeg(data: ByteArray) {
        val startIdx = findJpegStart(data)
        if (startIdx != -1) {
            val bitmap = try {
                BitmapFactory.decodeByteArray(data, startIdx, data.size - startIdx)
            } catch (e: Exception) { null }
            
            if (bitmap != null) {
                lastFrameTime = System.currentTimeMillis()
                _isFrameLive.value = true
                frameCounter++
                _latestFrame.value = CameraFrame(frameCounter, bitmap, bitmap.width, bitmap.height)
            }
        }
    }

    override fun stopStreaming() {
        _isStreaming.value = false
        _isFrameLive.value = false
        streamJob?.cancel()
    }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) return i
        }
        return -1
    }
}
