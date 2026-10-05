package com.visionmate.pro.ai

import android.util.Log
import com.visionmate.pro.model.Obstacle
import kotlin.math.abs

/**
 * Selects ONE relevant person from a list of YOLO-detected persons per frame.
 *
 * Relevance formula
 * -----------------
 *   relevanceScore = AREA_WEIGHT * normalizedBoxArea
 *                  + CENTER_PATH_WEIGHT * centerPathScore
 *
 *   normalizedBoxArea = (right - left) * (bottom - top)   [0, 1]
 *   centerPathScore   = 1 - 2 * |normalizedX - 0.5|       [0, 1]
 *
 * The centerPathScore peaks (1.0) for a person centred in the walking path
 * and falls to 0.0 at the far edges of the frame.
 *
 * Candidates with normalizedBoxArea < MIN_AREA_FOR_SELECTION are rejected
 * before scoring so that distant, tiny detections are ignored.
 */
object PersonCandidateSelector {

    private const val TAG = "PersonSelector"

    // ---- Configurable weights ------------------------------------------------

    /** Weight for normalised bounding-box area in the relevance score. */
    const val AREA_WEIGHT        = 0.60f

    /** Weight for proximity to the walking-path centre in the relevance score. */
    const val CENTER_PATH_WEIGHT = 0.40f

    /**
     * Minimum normalised box area to be considered as a candidate.
     * Below this the person is treated as too far away to matter.
     * Intentionally set slightly below PersonSizeClassifier.THRESHOLD_FAR_MAX (0.05)
     * to avoid hard rejection of borderline FAR detections.
     */
    const val MIN_AREA_FOR_SELECTION = 0.02f

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * The single best person candidate for this frame.
     *
     * @property obstacle       Original YOLO [Obstacle] (trackingId, boundingBox, confidence …).
     * @property normalizedBoxArea  Width * height of the bbox in [0, 1] space.
     * @property centerPathScore    How close to the frame centre (1 = centre, 0 = edge).
     * @property relevanceScore     Weighted combination used for selection.
     */
    data class ScoredCandidate(
        val obstacle: Obstacle,
        val normalizedBoxArea: Float,
        val centerPathScore: Float,
        val relevanceScore: Float
    ) {
        val areaPercent: String get() = "%.1f%%".format(normalizedBoxArea * 100f)
        val relevanceStr: String get() = "%.3f".format(relevanceScore)
    }

    // -------------------------------------------------------------------------
    // Selection
    // -------------------------------------------------------------------------

    /**
     * Select the single most relevant person from [persons].
     *
     * @param persons All [Obstacle] entries with objectType == PERSON from the
     *                current YOLO frame (typically filtered from top-10 detections).
     * @return The highest-scoring candidate, or null if none pass the area filter.
     */
    fun select(persons: List<Obstacle>): ScoredCandidate? {
        if (persons.isEmpty()) return null

        val candidates = persons.mapNotNull { person ->
            val width  = (person.boundingBox.right  - person.boundingBox.left).coerceAtLeast(0f)
            val height = (person.boundingBox.bottom - person.boundingBox.top).coerceAtLeast(0f)
            val area   = width * height

            // Reject detections that are too small / too far away
            if (area < MIN_AREA_FOR_SELECTION) {
                Log.v(TAG, "Rejected id=${person.id} area=${"%.1f%%".format(area * 100f)} (below min)")
                return@mapNotNull null
            }

            val normalizedX     = (person.boundingBox.left + person.boundingBox.right) / 2f
            val centerPathScore = (1f - 2f * abs(normalizedX - 0.5f)).coerceIn(0f, 1f)
            val relevance       = AREA_WEIGHT * area + CENTER_PATH_WEIGHT * centerPathScore

            ScoredCandidate(
                obstacle          = person,
                normalizedBoxArea = area,
                centerPathScore   = centerPathScore,
                relevanceScore    = relevance
            )
        }

        if (candidates.isEmpty()) return null

        val best = candidates.maxByOrNull { it.relevanceScore }!!

        if (candidates.size > 1) {
            Log.i(TAG, "Selected id=${best.obstacle.id} area=${best.areaPercent} " +
                "center=${"%.2f".format(best.centerPathScore)} relevance=${best.relevanceStr} " +
                "from ${candidates.size} valid candidates (${persons.size} total detected)")
        }

        return best
    }
}

// -----------------------------------------------------------------------------
// Identity Guard
// -----------------------------------------------------------------------------

/**
 * Detects when the selected person candidate has likely changed to a different
 * individual between consecutive frames and signals a history reset.
 *
 * YOLO anchor indices are NOT stable cross-frame, so the primary identity
 * signal is a sudden large jump in bounding-box area:  if the area changes
 * by more than [AREA_JUMP_THRESHOLD] in a single frame it is very likely that
 * the tracker switched to a different person.
 *
 * When YOLO *does* produce the same anchor index for two consecutive frames
 * (which can happen for dominant detections), that is used as a secondary
 * continuity hint — a matching ID lowers the threshold for a reset.
 */
class PersonIdentityGuard {

    companion object {
        private const val TAG = "PersonIdentity"

        /**
         * Single-frame area-ratio change that triggers a history reset.
         * A change of 0.40 (40 percentage points) is large enough to indicate
         * a different person while tolerating normal camera jitter.
         */
        const val AREA_JUMP_THRESHOLD = 0.40f
    }

    private var lastTrackingId: Int  = -1
    private var lastArea:       Float = 0f
    private var hasHistory:     Boolean = false

    /**
     * Update internal state with the current candidate and return whether the
     * approach-tracker history should be reset (identity likely changed).
     *
     * @param trackingId  Obstacle.trackingId (YOLO anchor index, not stable).
     * @param area        Normalised box area of the selected candidate.
     * @return true  → reset PersonApproachTracker and PersonAlertStateMachine.
     *         false → continue accumulating history.
     */
    fun checkAndUpdate(trackingId: Int, area: Float): Boolean {
        if (!hasHistory) {
            // First ever candidate — just record state, no reset needed
            lastTrackingId = trackingId
            lastArea       = area
            hasHistory     = true
            return false
        }

        val areaJump = abs(area - lastArea)
        val shouldReset = areaJump > AREA_JUMP_THRESHOLD

        if (shouldReset) {
            Log.i(TAG, "Identity reset: areaJump=${"%.3f".format(areaJump)} " +
                "(prev=${"%.1f%%".format(lastArea * 100f)} → now=${"%.1f%%".format(area * 100f)}) " +
                "prevId=$lastTrackingId nowId=$trackingId")
        }

        lastTrackingId = trackingId
        lastArea       = area
        return shouldReset
    }

    /** Call when no valid candidate exists so next valid frame starts fresh. */
    fun reset() {
        lastTrackingId = -1
        lastArea       = 0f
        hasHistory     = false
    }
}
