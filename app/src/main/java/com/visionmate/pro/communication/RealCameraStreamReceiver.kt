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
        const val STREAM_URL = "http://10.150.60.28:81/stream"
        private const val TAG = "CameraStream"
        private const val BUFFER_SIZE = 16384 // 16KB high-speed buffer
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = infinite streaming without timeout
        .retryOnConnectionFailure(true)
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
                delay(2000)
                val isLive = (System.currentTimeMillis() - lastFrameTime) < 5000
                _isFrameLive.value = isLive
            }
        }
    }

    override fun startStreaming(url: String) {
        if (_isStreaming.value) return
        Log.i(TAG, "Starting camera stream: $url")
        
        // List of candidate URLs to try in order
        val fallbackUrls = listOf(
            url,
            url.replace(":81/stream", ":80/stream"),
            url.replace(":81/stream", "/stream"),
            url.replace(":81/stream", ":80/capture")
        ).distinct()

        streamJob?.cancel()
        streamJob = scope.launch {
            var urlIndex = 0
            while (isActive) {
                val currentTargetUrl = fallbackUrls[urlIndex % fallbackUrls.size]
                try {
                    Log.d(TAG, "Connecting to stream: $currentTargetUrl")
                    val request = Request.Builder().url(currentTargetUrl).build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to connect to $currentTargetUrl: ${response.code}")
                        _isStreaming.value = false
                        _isFrameLive.value = false
                        response.close()
                        urlIndex++
                        delay(2000)
                        continue
                    }

                    _isStreaming.value = true
                    val inputStream = BufferedInputStream(response.body?.byteStream() ?: run {
                        response.close()
                        urlIndex++
                        delay(2000)
                        return@launch
                    })

                    val frameBuffer = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var prevByte = -1

                    while (isActive && _isStreaming.value) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break

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
                    response.close()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Stream error on $currentTargetUrl: ${e.message}")
                    _isStreaming.value = false
                    _isFrameLive.value = false
                    urlIndex++
                    delay(2000)
                }
            }
        }
    }

    private fun processJpeg(data: ByteArray) {
        val t0 = System.currentTimeMillis()
        val startIdx = findJpegStart(data)
        if (startIdx != -1) {
            val bitmap = try {
                BitmapFactory.decodeByteArray(data, startIdx, data.size - startIdx)
            } catch (e: Exception) { null }
            val decodeMs = System.currentTimeMillis() - t0
            
            if (bitmap != null) {
                val now = System.currentTimeMillis()
                lastFrameTime = now
                _isFrameLive.value = true
                frameCounter++
                _latestFrame.value = CameraFrame(frameCounter, bitmap, bitmap.width, bitmap.height, timestamp = now)
                Log.i(TAG, "DetectionTiming: FrameID=$frameCounter Decode=${decodeMs}ms Size=${bitmap.width}x${bitmap.height}")
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
