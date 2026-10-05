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

        private const val NUM_CLASSES = 80
        private const val NUM_DETECTIONS = 8400

        private const val BOX_VALUES = 4

        private const val CONFIDENCE_THRESHOLD = 0.40f

        private const val PERSON_CONFIDENCE_THRESHOLD = 0.35f
        private const val VEHICLE_CONFIDENCE_THRESHOLD = 0.35f

        private const val OBSTACLE_CONFIDENCE_THRESHOLD = 0.45f

        private const val MIN_BOX_AREA = 0.0025f

        private const val MIN_PERSON_BOX_AREA = 0.0010f

        private const val MAX_RETURNED_DETECTIONS = 10

        private const val MAX_LOW_PRIORITY_DETECTIONS = 4

        private const val NUM_THREADS = 4
    }

    /**
     * VisionMate-relevant COCO classes.
     */
    private val allowedLabels = setOf(
        "person",
        "bicycle", "car", "motorcycle", "bus", "truck", "train", "boat",
        "traffic light", "stop sign", "fire hydrant", "bench",
        "dog", "cat", "horse", "cow", "bird", "sheep", "elephant", "bear", "zebra", "giraffe",
        "chair", "couch", "potted plant", "bed", "dining table",
        // Prepared for custom model
        "staircase", "stairs", "branch", "tree branch"
    )

    private val highPriorityLabels = setOf(
        "person",
        "bicycle", "car", "motorcycle", "bus", "truck",
        "staircase", "stairs"
    )

    private val mediumPriorityLabels = setOf(
        "traffic light", "stop sign",
        "dog", "cat", "horse", "cow",
        "bench", "chair", "couch", "potted plant", "bed", "dining table",
        "branch", "tree branch"
    )

    private val lowPriorityLabels = setOf(
        "fire hydrant"
    )

    private val inputBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * 3 * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    private val floatBuffer: FloatBuffer =
        inputBuffer.asFloatBuffer()

    private val floatArray =
        FloatArray(INPUT_SIZE * INPUT_SIZE * 3)

    private val pixelBuffer =
        IntArray(INPUT_SIZE * INPUT_SIZE)

    private val normTable =
        FloatArray(256) { it / 255.0f }

    private val outputArray =
        Array(1) {
            Array(NUM_CLASSES + BOX_VALUES) {
                FloatArray(NUM_DETECTIONS)
            }
        }

    private var frameCounter = 0L

    init {
        val modelBuffer = loadModelFile(MODEL_FILE)

        val initResult = inferenceExecutor.submit(
            Callable {
                var selectedInterpreter: Interpreter? = null
                var selectedGpuDelegate: GpuDelegate? = null
                var delegateName = "CPU (XNNPACK)"

                try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val delegateOptions =
                            compatList.bestOptionsForThisDevice.apply {
                                setInferencePreference(
                                    GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
                                )
                                setPrecisionLossAllowed(false)
                            }
                        val delegate = GpuDelegate(delegateOptions)
                        val options = Interpreter.Options().apply {
                            addDelegate(delegate)
                        }
                        selectedInterpreter = Interpreter(modelBuffer, options)
                        selectedGpuDelegate = delegate
                        delegateName = "GPU Delegate"
                        Log.i(TAG, "GPU Delegate initialized successfully.")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "GPU initialization failed, using CPU.")
                    try { selectedGpuDelegate?.close() } catch (_: Exception) {}
                    selectedGpuDelegate = null
                    try { selectedInterpreter?.close() } catch (_: Exception) {}
                    selectedInterpreter = null
                }

                if (selectedInterpreter == null) {
                    val cpuOptions = Interpreter.Options().apply {
                        setNumThreads(NUM_THREADS)
                        setUseXNNPACK(true)
                    }
                    selectedInterpreter = Interpreter(modelBuffer, cpuOptions)
                    delegateName = "CPU (XNNPACK, $NUM_THREADS threads)"
                }
                Triple(selectedInterpreter, selectedGpuDelegate, delegateName)
            }
        ).get()

        interpreter = initResult.first
        gpuDelegate = initResult.second
        activeDelegateType = initResult.third
        labels = loadLabels()
    }

    override fun processFrame(
        frame: CameraFrame
    ): List<Obstacle> = synchronized(this) {
        val bitmap = frame.bitmap ?: return emptyList()
        if (bitmap.isRecycled) return emptyList()

        try {
            val t0 = System.nanoTime()
            val resizedBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            }
            val input = bitmapToByteBuffer(resizedBitmap)
            if (resizedBitmap != bitmap) resizedBitmap.recycle()

            inferenceExecutor.submit(Callable { interpreter.run(input, outputArray) }).get()

            val obstacles = parseOutput(outputArray[0], bitmap.width, bitmap.height)
            _detectedObstacles.value = obstacles
            
            frameCounter++
            Log.d(TAG, "Inference completed for frame ${frame.frameId}. Detected: ${obstacles.size}")

            return obstacles
        } catch (e: Exception) {
            Log.e(TAG, "Recoverable frame inference error: ${e.message}")
            return emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        bitmap.getPixels(pixelBuffer, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        var dstIdx = 0
        for (i in pixelBuffer.indices) {
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

    private fun parseOutput(output: Array<FloatArray>, imageWidth: Int, imageHeight: Int): List<Obstacle> {
        val candidates = mutableListOf<Obstacle>()
        for (i in 0 until NUM_DETECTIONS) {
            var bestClass = -1
            var bestConfidence = 0f

            for (classIndex in BOX_VALUES until NUM_CLASSES + BOX_VALUES) {
                val confidence = output[classIndex][i]
                if (confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestClass = classIndex - BOX_VALUES
                }
            }

            if (bestConfidence < CONFIDENCE_THRESHOLD || bestClass !in labels.indices) continue

            val label = labels[bestClass].trim().lowercase()
            if (label !in allowedLabels) continue

            val requiredConfidence = confidenceThresholdFor(label)
            if (bestConfidence < requiredConfidence) continue

            val centerX = output[0][i]
            val centerY = output[1][i]
            val width = output[2][i]
            val height = output[3][i]

            val left = (centerX - width / 2f).coerceIn(0f, 1f)
            val top = (centerY - height / 2f).coerceIn(0f, 1f)
            val right = (centerX + width / 2f).coerceIn(0f, 1f)
            val bottom = (centerY + height / 2f).coerceIn(0f, 1f)

            if ((right - left) * (bottom - top) < if (label == "person") MIN_PERSON_BOX_AREA else MIN_BOX_AREA) continue

            candidates.add(Obstacle(
                id = "yolo_${label}_$i",
                trackingId = i,
                objectType = mapObjectType(label),
                confidence = bestConfidence,
                boundingBox = BoundingBox(left, top, right, bottom),
                direction = if (centerX < INPUT_SIZE * 0.33f) Direction.LEFT else if (centerX > INPUT_SIZE * 0.66f) Direction.RIGHT else Direction.CENTER
            ))
        }

        val prioritized = candidates.sortedWith(
            compareByDescending<Obstacle> { priorityScore(it) }
                .thenByDescending { it.confidence }
                .thenByDescending { boundingBoxArea(it) }
        )

        val selected = mutableListOf<Obstacle>()
        var lowPriorityCount = 0
        for (obstacle in prioritized) {
            val label = labelFromObstacle(obstacle)
            if (label in lowPriorityLabels && lowPriorityCount >= MAX_LOW_PRIORITY_DETECTIONS) continue
            selected.add(obstacle)
            if (label in lowPriorityLabels) lowPriorityCount++
            if (selected.size >= MAX_RETURNED_DETECTIONS) break
        }

        return selected
    }

    private fun confidenceThresholdFor(label: String): Float = when {
        label == "person" -> PERSON_CONFIDENCE_THRESHOLD
        label in highPriorityLabels -> VEHICLE_CONFIDENCE_THRESHOLD
        else -> OBSTACLE_CONFIDENCE_THRESHOLD
    }

    private fun priorityScore(obstacle: Obstacle): Float {
        val label = labelFromObstacle(obstacle)
        val basePriority = when {
            label == "person" -> 100f
            label in highPriorityLabels -> 90f
            label in mediumPriorityLabels -> 60f
            label in lowPriorityLabels -> 30f
            else -> 0f
        }
        val sizeBonus = (boundingBoxArea(obstacle) * 50f).coerceIn(0f, 25f)
        return basePriority + sizeBonus + (obstacle.confidence * 15f)
    }

    private fun boundingBoxArea(obstacle: Obstacle): Float {
        val box = obstacle.boundingBox
        return (box.right - box.left).coerceAtLeast(0f) * (box.bottom - box.top).coerceAtLeast(0f)
    }

    private fun labelFromObstacle(obstacle: Obstacle): String {
        val id = obstacle.id
        if (id.startsWith("yolo_")) {
            val withoutPrefix = id.removePrefix("yolo_")
            val lastUnderscore = withoutPrefix.lastIndexOf('_')
            if (lastUnderscore > 0) return withoutPrefix.substring(0, lastUnderscore)
            return withoutPrefix
        }
        return "other"
    }

    private fun mapObjectType(label: String): ObjectType {
        return when (label.lowercase()) {
            "person" -> ObjectType.PERSON
            "car", "bus", "truck", "motorcycle", "bicycle", "train", "boat" -> ObjectType.VEHICLE
            "dog" -> ObjectType.DOG
            "cat" -> ObjectType.CAT
            "cow" -> ObjectType.COW
            "staircase", "stairs" -> ObjectType.STAIRCASE
            "tree branch", "branch" -> ObjectType.TREE_BRANCH
            "chair", "couch", "bed", "dining table", "bench" -> ObjectType.CHAIR
            "traffic light", "stop sign", "fire hydrant" -> ObjectType.STREET_OBJECT
            "horse", "sheep", "elephant", "bear", "zebra", "giraffe", "bird" -> ObjectType.ANIMAL
            else -> ObjectType.OTHER
        }
    }

    private fun loadModelFile(fileName: String): ByteBuffer {
        val fd = context.assets.openFd(fileName)
        return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun loadLabels(): List<String> = context.assets.open(LABELS_FILE).bufferedReader().readLines().filter { it.isNotBlank() }.map { it.trim() }
    private fun validateLabels() {}
}
