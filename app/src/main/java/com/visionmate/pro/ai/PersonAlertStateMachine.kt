package com.visionmate.pro.ai

import android.util.Log

/**
 * Four-state alert state machine for person detection.
 * Maintains one slot per Direction.ordinal (0=LEFT, 1=CENTER, 2=RIGHT).
 *
 * States
 * ------
 *   FAR -> MONITORING -> APPROACHING -> NEAR
 *
 * Rules
 * -----
 * - TTS fires ONLY on entry to APPROACHING or NEAR.
 * - Normal state changes require CONFIRM_FRAMES consecutive matching updates.
 * - NEAR entry is immediate (no confirmation) when smoothedRatio >= NEAR_ENTRY_THRESHOLD.
 * - NEAR exits via hysteresis: stays NEAR while smoothedRatio >= NEAR_EXIT_THRESHOLD.
 * - Secondary 3-second TTS cooldown prevents double-firing on rapid transitions.
 */
class PersonAlertStateMachine {

    companion object {
        private const val TAG = "PersonAlertSM"

        /** smoothedRatio threshold for immediate, confirmation-free NEAR entry. */
        const val NEAR_ENTRY_THRESHOLD = 0.20f

        /** Hysteresis: state stays NEAR while smoothedRatio >= this value. */
        const val NEAR_EXIT_THRESHOLD  = 0.18f

        /** Consecutive matching frames required for normal state transitions. */
        const val CONFIRM_FRAMES = 2

        /** Secondary cooldown: minimum ms between TTS events per slot. */
        const val TTS_COOLDOWN_MS = 3000L
    }

    // -------------------------------------------------------------------------
    // State + event types
    // -------------------------------------------------------------------------

    enum class PersonAlertState { FAR, MONITORING, APPROACHING, NEAR }

    /**
     * Fired when the machine transitions into APPROACHING or NEAR and the
     * TTS cooldown allows it.  The caller is responsible for speaking.
     */
    data class PersonAlertEvent(
        val newState: PersonAlertState,
        val trajectoryDirection: TrajectoryDirection,
        val slotKey: Int
    )

    // -------------------------------------------------------------------------
    // Per-slot internal state
    // -------------------------------------------------------------------------

    private data class SlotState(
        var currentState: PersonAlertState = PersonAlertState.FAR,
        var pendingState: PersonAlertState? = null,
        var pendingCount: Int = 0,
        var lastSpokenMs: Long = 0L
    )

    private val slots = mutableMapOf<Int, SlotState>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Feed the latest [PersonApproachResult] for a direction slot and return
     * a [PersonAlertEvent] if TTS should fire, or null otherwise.
     */
    fun update(approachResult: PersonApproachResult): PersonAlertEvent? {
        val slot = slots.getOrPut(approachResult.slotKey) { SlotState() }
        val now  = System.currentTimeMillis()

        // 1. Immediate NEAR entry (bypasses confirmation counter)
        if (approachResult.smoothedRatio >= NEAR_ENTRY_THRESHOLD) {
            return if (slot.currentState != PersonAlertState.NEAR) {
                Log.d(TAG, "Slot ${approachResult.slotKey}: immediate NEAR entry (ratio=${approachResult.smoothedRatioPercent})")
                commitTransition(PersonAlertState.NEAR, slot, approachResult, now)
            } else {
                slot.pendingState = null
                slot.pendingCount = 0
                null   // already NEAR, nothing to do
            }
        }

        // 2. NEAR hysteresis — stay NEAR until ratio drops below exit threshold
        if (slot.currentState == PersonAlertState.NEAR &&
            approachResult.smoothedRatio >= NEAR_EXIT_THRESHOLD) {
            slot.pendingState = null
            slot.pendingCount = 0
            return null
        }

        // 3. Determine desired state
        val desiredState = when {
            approachResult.isApproaching                                -> PersonAlertState.APPROACHING
            approachResult.smoothedSizeClass == PersonSizeClass.FAR     -> PersonAlertState.FAR
            else                                                        -> PersonAlertState.MONITORING
        }

        // 4. Already in desired state — reset pending counter
        if (desiredState == slot.currentState) {
            slot.pendingState = null
            slot.pendingCount = 0
            return null
        }

        // 5. Confirmation countdown
        if (desiredState != slot.pendingState) {
            slot.pendingState = desiredState
            slot.pendingCount = 1
            Log.d(TAG, "Slot ${approachResult.slotKey}: new pending=$desiredState (1/$CONFIRM_FRAMES)")
        } else {
            slot.pendingCount++
            Log.d(TAG, "Slot ${approachResult.slotKey}: pending=$desiredState (${slot.pendingCount}/$CONFIRM_FRAMES)")
        }

        // 6. Commit transition when confirmed
        return if (slot.pendingCount >= CONFIRM_FRAMES) {
            Log.d(TAG, "Slot ${approachResult.slotKey}: confirmed transition -> $desiredState")
            commitTransition(desiredState, slot, approachResult, now)
        } else {
            null
        }
    }

    /** Reset history for a slot (call when no person detected in that direction). */
    fun remove(slotKey: Int) {
        if (slots.remove(slotKey) != null) {
            Log.d(TAG, "Slot $slotKey: history cleared (person left frame)")
        }
    }

    /** Reset all slots. */
    fun clear() {
        slots.clear()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun commitTransition(
        newState: PersonAlertState,
        slot: SlotState,
        approachResult: PersonApproachResult,
        now: Long
    ): PersonAlertEvent? {
        slot.currentState = newState
        slot.pendingState = null
        slot.pendingCount = 0

        // Only emit events for actionable states
        if (newState != PersonAlertState.APPROACHING && newState != PersonAlertState.NEAR) {
            return null
        }

        // Secondary TTS cooldown safeguard
        if (now - slot.lastSpokenMs < TTS_COOLDOWN_MS) {
            Log.d(TAG, "Slot ${approachResult.slotKey}: $newState suppressed by TTS cooldown")
            return null
        }

        slot.lastSpokenMs = now
        return PersonAlertEvent(
            newState           = newState,
            trajectoryDirection = approachResult.trajectoryDirection,
            slotKey            = approachResult.slotKey
        )
    }
}
