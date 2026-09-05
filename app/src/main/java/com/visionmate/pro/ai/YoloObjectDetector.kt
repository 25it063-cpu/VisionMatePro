package com.visionmate.pro.ai

import android.content.Context
import android.graphics.Bitmap
import com.visionmate.pro.communication.CameraFrame
import com.visionmate.pro.model.BoundingBox
import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloObjectDetector(
    private val context: Context
) : ObjectDetector {

    private val _detectedObstacles =
        MutableStateFlow<List<Obstacle>>(emptyList())

    override val detectedObstacles: StateFlow<List<Obstacle>> =
        _detectedObstacles.asStateFlow()

    private val interpreter: Interpreter
    private val labels: List<String>

    companion object {
        private const val MODEL_FILE = "yolov8n_float32.tflite"
        private const val LABELS_FILE = "labels.txt"

        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.40f
    }

    init {
        interpreter = Interpreter(loadModelFile(MODEL_FILE))
        labels = loadLabels()
    }

    override fun processFrame(frame: CameraFrame): List<Obstacle> {

        val bitmap = frame.bitmap ?: return emptyList()

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            INPUT_SIZE,
            INPUT_SIZE,
            true
        )

        val input = bitmapToByteBuffer(resizedBitmap)

        val output = Array(1) {
            Array(84) {
                FloatArray(8400)
            }
        }

        interpreter.run(input, output)

        val obstacles = parseOutput(
            output[0],
            bitmap.width,
            bitmap.height
        )

        _detectedObstacles.value = obstacles

        return obstacles
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {

        val buffer = ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * 3 * 4
        )

        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        bitmap.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        for (pixel in pixels) {

            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        buffer.rewind()

        return buffer
    }

    private fun parseOutput(
        output: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Obstacle> {

        val obstacles = mutableListOf<Obstacle>()

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

            if (bestConfidence < CONFIDENCE_THRESHOLD) {
                continue
            }

            if (bestClass !in labels.indices) {
                continue
            }

            val label = labels[bestClass]

            val left = ((centerX - width / 2f) / INPUT_SIZE)
                .coerceIn(0f, 1f)

            val top = ((centerY - height / 2f) / INPUT_SIZE)
                .coerceIn(0f, 1f)

            val right = ((centerX + width / 2f) / INPUT_SIZE)
                .coerceIn(0f, 1f)

            val bottom = ((centerY + height / 2f) / INPUT_SIZE)
                .coerceIn(0f, 1f)

            val direction = when {

                centerX < INPUT_SIZE * 0.33f ->
                    Direction.LEFT

                centerX > INPUT_SIZE * 0.66f ->
                    Direction.RIGHT

                else ->
                    Direction.CENTER
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

        return obstacles
            .sortedByDescending { it.confidence }
            .take(10)
    }

    private fun mapObjectType(label: String): ObjectType {

        return when (label.lowercase()) {

            "person" ->
                ObjectType.PERSON

            "car",
            "bus",
            "truck",
            "motorcycle",
            "bicycle" ->
                ObjectType.VEHICLE

            "chair" ->
                ObjectType.CHAIR

            else ->
                ObjectType.OTHER
        }
    }

    private fun loadModelFile(fileName: String): ByteBuffer {

        val fileDescriptor =
            context.assets.openFd(fileName)

        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)

        val fileChannel =
            inputStream.channel

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