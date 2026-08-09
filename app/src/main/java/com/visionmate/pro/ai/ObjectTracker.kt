package com.visionmate.pro.ai

import com.visionmate.pro.model.Obstacle

class TrackedObject(
    val trackingId: Int,
    var obstacle: Obstacle,
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    var firstSeenTimestamp: Long = System.currentTimeMillis()
)

class ObjectTracker {

    private val trackedObjectsMap = mutableMapOf<Int, TrackedObject>()

    fun updateTracks(newDetections: List<Obstacle>): List<Obstacle> {
        val now = System.currentTimeMillis()
        val updatedList = mutableListOf<Obstacle>()

        for (detection in newDetections) {
            val existing = trackedObjectsMap[detection.trackingId]
            if (existing != null) {
                existing.obstacle = detection
                existing.lastSeenTimestamp = now
                updatedList.add(detection)
            } else {
                val newTrack = TrackedObject(
                    trackingId = detection.trackingId,
                    obstacle = detection,
                    lastSeenTimestamp = now,
                    firstSeenTimestamp = now
                )
                trackedObjectsMap[detection.trackingId] = newTrack
                updatedList.add(detection)
            }
        }

        // Clean stale tracks older than 1.5 seconds
        val iterator = trackedObjectsMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenTimestamp > 1500) {
                iterator.remove()
            }
        }

        return updatedList
    }

    fun getTrackedObject(trackingId: Int): TrackedObject? {
        return trackedObjectsMap[trackingId]
    }
}
