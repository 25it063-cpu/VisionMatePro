package com.visionmate.pro.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.visionmate.pro.communication.CameraFrame
import com.visionmate.pro.model.BoundingBox
import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

import java.util.concurrent.Callable
import java.util.concurrent.Executors

class YoloObjectDetector(
    private val context: Context
) : ObjectDetector {

    private val _detectedObstacles =
        MutableStateFlow<List<Obstacle>>(emptyList())

    override val detectedObstacles: StateFlow<List<Obstacle>> =
        _detectedObstacles.asStateFlow()

    private var interpreter: Interpreter
    private val labels: List<String>
    private var gpuDelegate: GpuDelegate? = null
    private var activeDelegateType = "CPU (XNNPACK)"

    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YoloInferenceThread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }

    companion object {
        private const val TAG = "YoloDetector"
        private const val MODEL_FILE = "yolov8n_float32.tflite"
        private const val LABELS_FILE = "labels.txt"

        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.40f
        private const val NUM_THREADS = 4
    }

    private val inputBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * 3 * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val floatBuffer: FloatBuffer = inputBuffer.asFloatBuffer()
    private val floatArray = FloatArray(INPUT_SIZE * INPUT_SIZE * 3)
    private val pixelBuffer = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val normTable = FloatArray(256) { it / 255.0f }
    private val outputArray = Array(1) {
        Array(84) {
            FloatArray(8400)
        }
    }
    private var frameCounter = 0L

    init {
        val modelBuffer = loadModelFile(MODEL_FILE)
        
        // Initialize on the dedicated inference thread to guarantee OpenGL/EGL thread context affinity
        val initResult = inferenceExecutor.submit(Callable {
            var selectedInterpreter: Interpreter? = null
            var selectedGpuDelegate: GpuDelegate? = null
            var delegateName = "CPU (XNNPACK)"

            // 1. Attempt GPU Delegate on dedicated inference thread
            try {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice.apply {
                        setInferencePreference(GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                        setPrecisionLossAllowed(false) // Preserve Float32 precision for accurate detections
                    }
                    val delegate = GpuDelegate(delegateOptions)
                    val options = Interpreter.Options().apply {
                        addDelegate(delegate)
                    }
                    val testInterpreter = Interpreter(modelBuffer, options)
                    
                    // Warm-up inference on the dedicated thread to verify GPU execution
                    testInterpreter.run(inputBuffer, outputArray)
                    
                    selectedInterpreter = testInterpreter
                    selectedGpuDelegate = delegate
                    delegateName = "GPU Delegate"
                    Log.i(TAG, "TFLite GPU Delegate initialized and verified successfully on dedicated inference thread.")
                } else {
                    Log.i(TAG, "CompatibilityList reports GPU Delegate not supported on this device.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "GPU Delegate initialization failed on dedicated thread: ${e.message}. Falling back to XNNPACK CPU.", e)
                try { selectedGpuDelegate?.close() } catch (_: Exception) {}
                selectedGpuDelegate = null
                try { selectedInterpreter?.close() } catch (_: Exception) {}
                selectedInterpreter = null
            }

            // 2. Clean fallback to CPU with XNNPACK on 4 threads if GPU cannot be used
            if (selectedInterpreter == null) {
                val cpuOptions = Interpreter.Options().apply {
                    setNumThreads(NUM_THREADS)
                    setUseXNNPACK(true)
                }
                selectedInterpreter = Interpreter(modelBuffer, cpuOptions)
                delegateName = "CPU (XNNPACK, $NUM_THREADS threads)"
                Log.i(TAG, "Initialized TFLite on CPU with XNNPACK on dedicated inference thread.")
            }

            Triple(selectedInterpreter, selectedGpuDelegate, delegateName)
        }).get()

        interpreter = initResult.first
        gpuDelegate = initResult.second
        activeDelegateType = initResult.third
        labels = loadLabels()
        Log.i(TAG, "TFLite initialization complete: Active Delegate = $activeDelegateType")
    }

    override fun processFrame(frame: CameraFrame): List<Obstacle> = synchronized(this) {
        val bitmap = frame.bitmap ?: return emptyList()
        if (bitmap.isRecycled) return emptyList()

        try {
            val receiveTime = System.currentTimeMillis()
            val frameAgeMs = receiveTime - frame.timestamp
            val t0 = System.nanoTime()

            // 1. Manual Bitmap resize
            val resizedBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(
                    bitmap,
                    INPUT_SIZE,
                    INPUT_SIZE,
                    true
                )
            }
            val t1 = System.nanoTime()

            // 2. Manual RGB float extraction (optimized bulk transfer)
            val input = bitmapToByteBuffer(resizedBitmap)

            if (resizedBitmap != bitmap && !resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }
            val t2 = System.nanoTime()

            // 3. TFLite inference on the EXACT same dedicated thread
            val t3 = System.nanoTime()
            inferenceExecutor.submit(Callable {
                try {
                    interpreter.run(input, outputArray)
                } catch (e: Throwable) {
                    if (gpuDelegate != null) {
                        Log.w(TAG, "GPU inference failed at runtime: ${e.message}. Reverting to CPU XNNPACK.", e)
                        try { gpuDelegate?.close() } catch (_: Exception) {}
                        gpuDelegate = null
                        val cpuOptions = Interpreter.Options().apply {
                            setNumThreads(NUM_THREADS)
                            setUseXNNPACK(true)
                        }
                        interpreter = Interpreter(loadModelFile(MODEL_FILE), cpuOptions)
                        activeDelegateType = "CPU (XNNPACK Runtime Fallback)"
                        interpreter.run(input, outputArray)
                    } else {
                        throw e
                    }
                }
            }).get()
            val tEnd = System.nanoTime()

            // 4. Output parsing
            val obstacles = parseOutput(
                outputArray[0],
                bitmap.width,
                bitmap.height
            )

            _detectedObstacles.value = obstacles
            val t4 = System.nanoTime()

            val resizeMs = (t1 - t0) / 1_000_000.0
            val preprocessMs = (t2 - t1) / 1_000_000.0
            val inferenceMs = (tEnd - t3) / 1_000_000.0
            val parseMs = (t4 - tEnd) / 1_000_000.0
            val totalMs = (t4 - t0) / 1_000_000.0

            frameCounter++
            Log.i(TAG, "DetectionTiming: FrameID=${frame.frameId} Age=${frameAgeMs}ms Resize=%.1fms Preprocess=%.1fms Inference=%.1fms Parse=%.1fms Total=%.1fms (Delegate=$activeDelegateType)".format(
                resizeMs, preprocessMs, inferenceMs, parseMs, totalMs
            ))

            return obstacles
        } catch (e: Exception) {
            Log.e(TAG, "Recoverable frame inference error: ${e.message}", e)
            return emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        bitmap.getPixels(
            pixelBuffer,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        var dstIdx = 0
        for (i in 0 until pixelBuffer.size) {
            val pixel = pixelBuffer[i]
            floatArray[dstIdx++] = normTable[(pixel shr 16) and 0xFF]
            floatArray[dstIdx++] = normTable[(pixel shr 8) and 0xFF]
            floatArray[dstIdx++] = normTable[pixel and 0xFF]
        }

        floatBuffer.rewind()
        floatBuffer.put(floatArray)
        inputBuffer.rewind()
        return inputBuffer
    }

    private fun parseOutput(
        output: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        var globalMaxConfidence = 0f
        var globalBestClass = -1

        for (i in 0 until 8400) {
            val centerX = output[0][i]
            val centerY = output[1][i]
            val width = output[2][i]
            val height = output[3][i]

            var bestClass = -1
            var bestConfidence = 0f

            for (classIndex in 4 until 84) {
                val confidence = output[classIndex][i]
                if (confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestClass = classIndex - 4
                }
            }

            if (bestConfidence > globalMaxConfidence) {
                globalMaxConfidence = bestConfidence
                globalBestClass = bestClass
            }

            if (bestConfidence < CONFIDENCE_THRESHOLD) {
                continue
            }

            if (bestClass !in labels.indices) {
                continue
            }

            val label = labels[bestClass]

            val left = ((centerX - width / 2f) / INPUT_SIZE).coerceIn(0f, 1f)
            val top = ((centerY - height / 2f) / INPUT_SIZE).coerceIn(0f, 1f)
            val right = ((centerX + width / 2f) / INPUT_SIZE).coerceIn(0f, 1f)
            val bottom = ((centerY + height / 2f) / INPUT_SIZE).coerceIn(0f, 1f)

            val direction = when {
                centerX < INPUT_SIZE * 0.33f -> Direction.LEFT
                centerX > INPUT_SIZE * 0.66f -> Direction.RIGHT
                else -> Direction.CENTER
            }

            val objectType = mapObjectType(label)

            obstacles.add(
                Obstacle(
                    id = "yolo_${label}_$i",
                    trackingId = i,
                    objectType = objectType,
                    confidence = bestConfidence,
                    boundingBox = BoundingBox(
                        left,
                        top,
                        right,
                        bottom
                    ),
                    direction = direction
                )
            )
        }

        val sorted = obstacles.sortedByDescending { it.confidence }

        if (sorted.isNotEmpty()) {
            val top5 = sorted.take(5).joinToString("; ") { obs ->
                "[label=${obs.id}, type=${obs.objectType}, conf=%.2f, dir=${obs.direction}, box=(%.2f,%.2f,%.2f,%.2f)]".format(
                    obs.confidence, obs.boundingBox.left, obs.boundingBox.top, obs.boundingBox.right, obs.boundingBox.bottom
                )
            }
            Log.i(TAG, "YOLO Output (${sorted.size} candidates >= $CONFIDENCE_THRESHOLD): $top5")
        } else {
            val maxLabel = if (globalBestClass in labels.indices) labels[globalBestClass] else "none"
            Log.i(TAG, "YOLO Output: 0 candidates >= $CONFIDENCE_THRESHOLD (highest: '$maxLabel' conf=%.2f)".format(globalMaxConfidence))
        }

        return sorted.take(10)
    }

    private fun mapObjectType(label: String): ObjectType {
        return when (label.lowercase()) {
            "person" -> ObjectType.PERSON

            // Vehicles
            "car", "bus", "truck", "motorcycle", "bicycle", "train", "airplane", "boat" -> ObjectType.VEHICLE

            // Furniture
            "chair", "couch", "bed", "dining table", "bench" -> ObjectType.CHAIR

            // Animals
            "cat", "dog", "bird", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe" -> ObjectType.ANIMAL

            // Street Objects (Mapped to Pole)
            "traffic light", "stop sign", "fire hydrant", "parking meter" -> ObjectType.POLE

            // Household items / Potential obstacles
            "backpack", "umbrella", "handbag", "suitcase", "bottle", "cup", "tv", "laptop", "refrigerator" -> ObjectType.OTHER

            else -> ObjectType.OTHER
        }
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun loadLabels(): List<String> {
        return context.assets
            .open(LABELS_FILE)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
    }
}
