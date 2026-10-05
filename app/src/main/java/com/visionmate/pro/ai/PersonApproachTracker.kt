package com.visionmate.pro.ai

/**
 * Tracks smoothed bounding-box area ratios AND horizontal position for people
 * across consecutive YOLO frames, then classifies their movement as either
 * genuinely APPROACHING or only sideways (MONITORING/SILENT).
 *
 * Keying strategy
 * ---------------
 * YOLO anchor indices are NOT stable across frames, so this tracker uses
 * Direction.ordinal (0=LEFT, 1=CENTER, 2=RIGHT) as the stable per-slot key.
 *
 * Smoothing
 * ---------
 * Up to WINDOW_SIZE samples are retained per slot.
 * Samples older than HISTORY_WINDOW_MS are discarded on the next update.
 * smoothedRatio = simple moving average of all retained area-ratio samples.
 *
 * Approach detection (ALL four conditions must be true)
 * -----------------------------------------------------
 * 1. smoothedRatio  >= MIN_AREA_FOR_APPROACHING  (person is large enough)
 * 2. growthPercent  >= APPROACHING_GROWTH_PERCENT (area is growing)
 * 3. horizontalDrift <= MAX_HORIZONTAL_DRIFT      (not moving strongly sideways)
 * 4. halfSize       >= MIN_HALF_SIZE              (enough history exists)
 *
 *   growthPercent  = ((secondHalfAreaAvg - firstHalfAreaAvg) / firstHalfAreaAvg) * 100
 *   horizontalDrift = max(normalizedX in window) - min(normalizedX in window)
 *
 * If the box grows but drifts beyond MAX_HORIZONTAL_DRIFT the person is
 * classified as MONITORING (moving sideways), not APPROACHING.
 *
 * Trajectory direction thresholds (configurable, independent of YOLO direction)
 * -------------------------------------------------------------------------------
 *   normalizedX < TRAJ_LEFT_THRESHOLD          -> LEFT
 *   TRAJ_LEFT_THRESHOLD .. TRAJ_RIGHT_THRESHOLD -> CENTER
 *   > TRAJ_RIGHT_THRESHOLD                     -> RIGHT
 *
 * normalizedX = (bbox.left + bbox.right) / 2  [already in 0-1 range]
 */
class PersonApproachTracker {

    companion object {
        /** Maximum samples retained per direction slot. */
        const val WINDOW_SIZE = 5

        /** Samples older than this many milliseconds are discarded. */
        const val HISTORY_WINDOW_MS = 1200L

        /**
         * Minimum samples required in EACH half for approach/drift analysis.
         * With WINDOW_SIZE = 5 this means >= 4 total samples are needed.
         */
        private const val MIN_HALF_SIZE = 2

        // ---- Approach gate conditions ----------------------------------------

        /** Minimum smoothed area ratio for APPROACHING to be declared. */
        const val MIN_AREA_FOR_APPROACHING = 0.08f

        /** Minimum area growth (%) between first and second window halves. */
        const val APPROACHING_GROWTH_PERCENT = 5f

        /**
         * Maximum allowed range of normalizedX across the window.
         * If max(normalizedX) - min(normalizedX) exceeds this, the person is
         * moving strongly sideways and is classified MONITORING, not APPROACHING.
         */
        const val MAX_HORIZONTAL_DRIFT = 0.25f

        // ---- Trajectory direction thresholds ---------------------------------

        /** normalizedX below this -> trajectory direction LEFT. */
        const val TRAJ_LEFT_THRESHOLD  = 0.40f

        /** normalizedX above this -> trajectory direction RIGHT. */
        const val TRAJ_RIGHT_THRESHOLD = 0.60f
    }

    /** One sample: area ratio + horizontal centre + timestamp. */
    private data class Sample(
        val ratio: Float,
        val normalizedX: Float,
        val timestampMs: Long
    )

    /**
     * One window per direction slot (Direction.ordinal -> deque of samples).
     * Keys: 0 = LEFT, 1 = CENTER, 2 = RIGHT.
     */
    private val slots = mutableMapOf<Int, ArrayDeque<Sample>>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Record a new observation for [slotKey] and return the current state.
     *
     * @param slotKey      Stable per-region key (Direction.ordinal).
     * @param boxAreaRatio Normalised bbox area ratio (width x height, 0-1).
     * @param normalizedX  Horizontal centre of the bbox, already in [0, 1].
     *                     Compute as (bbox.left + bbox.right) / 2.
     * @param timestampMs  Observation time; defaults to System.currentTimeMillis().
     */
    fun update(
        slotKey: Int,
        boxAreaRatio: Float,
        normalizedX: Float,
        timestampMs: Long = System.currentTimeMillis()
    ): PersonApproachResult {
        val deque = slots.getOrPut(slotKey) { ArrayDeque() }

        // 1. Append new sample
        deque.addLast(Sample(boxAreaRatio, normalizedX, timestampMs))

        // 2. Enforce max window size (oldest-first eviction)
        while (deque.size > WINDOW_SIZE) deque.removeFirst()

        // 3. Discard samples that are too old
        val cutoffMs = timestampMs - HISTORY_WINDOW_MS
        while (deque.isNotEmpty() && deque.first().timestampMs < cutoffMs) {
            deque.removeFirst()
        }

        return buildResult(slotKey, deque)
    }

    /**
     * Remove all history for a slot (call when no person is detected in that
     * direction for a given frame to prevent stale state from accumulating).
     */
    fun remove(slotKey: Int) {
        slots.remove(slotKey)
    }

    /** Remove all history for every slot. */
    fun clear() {
        slots.clear()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun buildResult(slotKey: Int, deque: ArrayDeque<Sample>): PersonApproachResult {
        val n = deque.size

        // --- Smoothed area ratio (moving average) ---
        val ratios = deque.map { it.ratio }
        val smoothedRatio = if (n > 0) ratios.average().toFloat() else 0f
        val smoothedClass = PersonSizeClassifier.classify(smoothedRatio)

        // --- Current normalizedX (most recent sample) ---
        val currentNormalizedX = if (n > 0) deque.last().normalizedX else 0.5f

        // --- Trajectory direction (based on current normalizedX) ---
        val trajectoryDirection = when {
            currentNormalizedX < TRAJ_LEFT_THRESHOLD  -> TrajectoryDirection.LEFT
            currentNormalizedX > TRAJ_RIGHT_THRESHOLD -> TrajectoryDirection.RIGHT
            else                                      -> TrajectoryDirection.CENTER
        }

        // --- Horizontal drift (range of normalizedX across the window) ---
        val allX = deque.map { it.normalizedX }
        val horizontalDrift = if (n > 1) {
            (allX.max() - allX.min())
        } else {
            0f   // single sample: no drift yet
        }
        val isMovingSideways = horizontalDrift > MAX_HORIZONTAL_DRIFT

        // --- Area growth (half-window comparison) ---
        val halfSize = n / 2   // integer division; middle sample ignored for odd n
        val growthPercent: Float?
        val hasEnoughHistory = halfSize >= MIN_HALF_SIZE

        if (hasEnoughHistory) {
            val firstHalfAvg  = ratios.take(halfSize).average().toFloat()
            val secondHalfAvg = ratios.takeLast(halfSize).average().toFloat()
            growthPercent = if (firstHalfAvg > 0f) {
                ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100f
            } else {
                null   // degenerate: first half area is zero
            }
        } else {
            growthPercent = null
        }

        // --- APPROACHING: all four gates must pass ---
        val isApproaching = hasEnoughHistory &&
                smoothedRatio  >= MIN_AREA_FOR_APPROACHING &&
                growthPercent  != null &&
                growthPercent  >= APPROACHING_GROWTH_PERCENT &&
                !isMovingSideways

        return PersonApproachResult(
            slotKey            = slotKey,
            smoothedRatio      = smoothedRatio,
            smoothedSizeClass  = smoothedClass,
            isApproaching      = isApproaching,
            growthPercent      = growthPercent,
            sampleCount        = n,
            currentNormalizedX = currentNormalizedX,
            trajectoryDirection = trajectoryDirection,
            horizontalDrift    = horizontalDrift,
            isMovingSideways   = isMovingSideways
        )
    }
}

// -----------------------------------------------------------------------------
// Trajectory direction (independent thresholds from YOLO bounding-box direction)
// -----------------------------------------------------------------------------

/**
 * Horizontal movement direction based on trajectory-analysis thresholds,
 * which differ from the raw YOLO screen-region split (0.33 / 0.66).
 *
 * LEFT   -> normalizedX < 0.40
 * CENTER -> 0.40 .. 0.60
 * RIGHT  -> normalizedX > 0.60
 */
enum class TrajectoryDirection {
    LEFT, CENTER, RIGHT;
    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

// -----------------------------------------------------------------------------
// Result model
// -----------------------------------------------------------------------------

/**
 * Full snapshot of one direction slot after an [PersonApproachTracker.update] call.
 *
 * @property slotKey             Direction.ordinal used as the slot key.
 * @property smoothedRatio       Moving average of area ratios in the window.
 * @property smoothedSizeClass   [PersonSizeClass] from [smoothedRatio].
 * @property isApproaching       True only when ALL four gates pass:
 *                               area >= 0.12, growth >= 20%, drift <= 0.15, enough history.
 * @property growthPercent       Area growth trend %; null when < 4 samples.
 * @property sampleCount         Number of samples currently retained.
 * @property currentNormalizedX  Horizontal centre from the latest sample (0-1).
 * @property trajectoryDirection Horizontal movement classification.
 * @property horizontalDrift     max(normalizedX) - min(normalizedX) across window.
 * @property isMovingSideways    True when [horizontalDrift] > MAX_HORIZONTAL_DRIFT.
 */
data class PersonApproachResult(
    val slotKey: Int,
    val smoothedRatio: Float,
    val smoothedSizeClass: PersonSizeClass,
    val isApproaching: Boolean,
    val growthPercent: Float?,
    val sampleCount: Int,
    val currentNormalizedX: Float,
    val trajectoryDirection: TrajectoryDirection,
    val horizontalDrift: Float,
    val isMovingSideways: Boolean
) {
    val smoothedRatioPercent: String
        get() = "%.1f%%".format(smoothedRatio * 100f)

    val growthStr: String
        get() = if (growthPercent != null) "%.1f%%".format(growthPercent) else "n/a"

    val driftStr: String
        get() = "%.3f".format(horizontalDrift)

    override fun toString(): String =
        "PersonApproach(slot=$slotKey samples=$sampleCount " +
        "avg=$smoothedRatioPercent class=${smoothedSizeClass.label} " +
        "growth=$growthStr drift=$driftStr sideways=$isMovingSideways " +
        "trajDir=${trajectoryDirection.label} approaching=$isApproaching)"
}
