package com.visionmate.pro.ai

import com.visionmate.pro.communication.CameraFrame
import com.visionmate.pro.model.BoundingBox
import com.visionmate.pro.model.Direction
import com.visionmate.pro.model.ObjectType
import com.visionmate.pro.model.Obstacle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

interface ObjectDetector {
    val detectedObstacles: StateFlow<List<Obstacle>>
    fun processFrame(frame: CameraFrame): List<Obstacle>
}

class MockObjectDetector : ObjectDetector {

    private val _detectedObstacles = MutableStateFlow<List<Obstacle>>(emptyList())
    override val detectedObstacles: StateFlow<List<Obstacle>> = _detectedObstacles.asStateFlow()

    private var simulationStep = 0

    override fun processFrame(frame: CameraFrame): List<Obstacle> {
        simulationStep++

        val obstacles = mutableListOf<Obstacle>()

        // Periodically simulate different obstacle scenarios
        when (simulationStep % 30) {
            in 4..8 -> {
                // Staircase ahead in center
                obstacles.add(
                    Obstacle(
                        id = "obj_staircase_1",
                        trackingId = 101,
                        objectType = ObjectType.STAIRCASE,
                        confidence = 0.92f,
                        boundingBox = BoundingBox(0.35f, 0.40f, 0.65f, 0.90f),
                        direction = Direction.CENTER
                    )
                )
            }
            in 12..16 -> {
                // Pole on the right
                obstacles.add(
                    Obstacle(
                        id = "obj_pole_1",
                        trackingId = 102,
                        objectType = ObjectType.POLE,
                        confidence = 0.88f,
                        boundingBox = BoundingBox(0.70f, 0.20f, 0.90f, 0.85f),
                        direction = Direction.RIGHT
                    )
                )
            }
            in 20..24 -> {
                // Upper obstacle: tree branch above (no ultrasonic distance available)
                obstacles.add(
                    Obstacle(
                        id = "obj_tree_branch_1",
                        trackingId = 103,
                        objectType = ObjectType.TREE_BRANCH,
                        confidence = 0.94f,
                        boundingBox = BoundingBox(0.30f, 0.05f, 0.70f, 0.35f),
                        direction = Direction.CENTER,
                        isUpperObstacle = true
                    )
                )
            }
            in 26..28 -> {
                // Person approaching on left
                obstacles.add(
                    Obstacle(
                        id = "obj_person_1",
                        trackingId = 104,
                        objectType = ObjectType.PERSON,
                        confidence = 0.90f,
                        boundingBox = BoundingBox(0.10f, 0.20f, 0.35f, 0.80f),
                        direction = Direction.LEFT
                    )
                )
            }
            else -> {
                // Path clear
            }
        }

        _detectedObstacles.value = obstacles
        return obstacles
    }
}
