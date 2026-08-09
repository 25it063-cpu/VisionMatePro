package com.visionmate.pro.ai

import com.visionmate.pro.model.BoundingBox
import com.visionmate.pro.model.Direction

object DirectionDetector {

    fun determineDirection(box: BoundingBox): Direction {
        val centerX = (box.left + box.right) / 2.0f
        return when {
            centerX < 0.38f -> Direction.LEFT
            centerX > 0.62f -> Direction.RIGHT
            else -> Direction.CENTER
        }
    }
}
