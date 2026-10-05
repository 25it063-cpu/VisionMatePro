package com.visionmate.pro.ai

import com.visionmate.pro.model.BoundingBox

/**
 * Classifies how large a detected person appears in the camera frame based on
 * the ratio of the bounding-box area to the total frame area.
 *
 * Because bounding boxes are already normalised to [0, 1] the "frame area" is
 * always 1.0 x 1.0 = 1.0, so the ratio is simply:
 *
 *   boxAreaRatio = boxWidth x boxHeight
 *
 * No ultrasonic distance data is used.  Classification is purely visual.
 */
object PersonSizeClassifier {

    // -------------------------------------------------------------------------
    // Configurable thresholds  (boxAreaRatio = (right-left) x (bottom-top))
    // -------------------------------------------------------------------------

    /** Anything below this is too far away to matter. */
    const val THRESHOLD_FAR_MAX        = 0.05f   // [0.00, 0.05)  -> FAR

    /** Between FAR and RELEVANT -- start watching but do not act yet. */
    const val THRESHOLD_MONITORING_MAX = 0.12f   // [0.05, 0.12)  -> MONITORING

    /** Person is large enough to be relevant / should be actively tracked. */
    const val THRESHOLD_RELEVANT_MAX   = 0.25f   // [0.12, 0.25)  -> RELEVANT

    // >= THRESHOLD_RELEVANT_MAX  ->  NEAR (person is very close)

    // -------------------------------------------------------------------------
    // Classification
    // -------------------------------------------------------------------------

    /**
     * Returns the [PersonSizeClass] for a bounding box whose coordinates are
     * normalised to the full camera-frame dimensions ([0, 1] range).
     *
     * @param box Normalised bounding box (left, top, right, bottom in [0, 1]).
     */
    fun classify(box: BoundingBox): PersonSizeClass {
        val width  = (box.right  - box.left).coerceAtLeast(0f)
        val height = (box.bottom - box.top).coerceAtLeast(0f)
        val ratio  = width * height
        return classify(ratio)
    }

    /**
     * Returns the [PersonSizeClass] for a pre-computed area ratio.
     *
     * @param boxAreaRatio  boxWidth x boxHeight, both normalised to [0, 1].
     */
    fun classify(boxAreaRatio: Float): PersonSizeClass = when {
        boxAreaRatio <  THRESHOLD_FAR_MAX        -> PersonSizeClass.FAR
        boxAreaRatio <  THRESHOLD_MONITORING_MAX -> PersonSizeClass.MONITORING
        boxAreaRatio <  THRESHOLD_RELEVANT_MAX   -> PersonSizeClass.RELEVANT
        else                                     -> PersonSizeClass.NEAR
    }

    /**
     * Convenience: computes the normalised area ratio from a bounding box and
     * returns it alongside the classification.
     *
     * @return [PersonSizeResult] containing both the ratio and the class.
     */
    fun classifyWithRatio(box: BoundingBox): PersonSizeResult {
        val width  = (box.right  - box.left).coerceAtLeast(0f)
        val height = (box.bottom - box.top).coerceAtLeast(0f)
        val ratio  = width * height
        return PersonSizeResult(
            boxAreaRatio = ratio,
            sizeClass    = classify(ratio)
        )
    }
}

// -----------------------------------------------------------------------------
// Model types
// -----------------------------------------------------------------------------

/**
 * Visual proximity class based on the person's apparent size in the frame.
 *
 * FAR        -- box covers < 5% of the frame.   Person is far away.
 * MONITORING -- box covers 5-12% of the frame.  Person is approaching; watch.
 * RELEVANT   -- box covers 12-25% of the frame. Person is close enough to track.
 * NEAR       -- box covers >= 25% of the frame. Person is very close.
 */
enum class PersonSizeClass {
    FAR,
    MONITORING,
    RELEVANT,
    NEAR;

    /** Human-readable label for logging / display. */
    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Result returned by [PersonSizeClassifier.classifyWithRatio].
 *
 * @property boxAreaRatio  Normalised bounding-box area (width x height, 0-1).
 * @property sizeClass     The resulting [PersonSizeClass].
 */
data class PersonSizeResult(
    val boxAreaRatio: Float,
    val sizeClass: PersonSizeClass
) {
    /** Formatted ratio as a percentage string, e.g. "8.3%". */
    val ratioPercent: String get() = "%.1f%%".format(boxAreaRatio * 100f)

    override fun toString(): String =
        "PersonSizeResult(ratio=$ratioPercent, class=${sizeClass.label})"
}
