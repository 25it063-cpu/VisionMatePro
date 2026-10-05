package com.visionmate.pro.model

enum class ObjectType {
    PERSON,
    STAIRCASE,
    TREE_BRANCH,
    POLE,
    CHAIR,
    VEHICLE,
    DOOR,
    WALL,
    WATER,
    ANIMAL,
    DOG,
    CAT,
    COW,
    STREET_OBJECT,
    HOUSEHOLD_ITEM,
    OTHER
}

enum class Direction {
    LEFT,
    CENTER,
    RIGHT
}

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val isUpperObstacle: Boolean
        get() = top < 0.35f && bottom < 0.60f

    companion object {
        fun empty() = BoundingBox(0f, 0f, 0f, 0f)
    }
}

data class Obstacle(
    val id: String,
    val trackingId: Int,
    val objectType: ObjectType,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val direction: Direction,
    val measuredDistanceCm: Int? = null,
    val isUpperObstacle: Boolean = (objectType == ObjectType.TREE_BRANCH || boundingBox.isUpperObstacle),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun unknown(direction: Direction) = Obstacle(
            id = "unknown_${direction.name}",
            trackingId = -1,
            objectType = ObjectType.OTHER,
            confidence = 1.0f,
            boundingBox = BoundingBox.empty(),
            direction = direction
        )
    }
}
