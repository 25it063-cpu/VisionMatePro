package com.visionmate.pro.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticManager(
    private val context: Context
) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Short single pulse for nearby obstacle (Caution / Orange state)
     */
    fun vibrateNearby() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(120)
        }
    }

    /**
     * Strong rapid double pulse for critical proximity (STOP / Red state)
     */
    fun vibrateVeryClose() {
        val pattern = longArrayOf(0, 150, 80, 250)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    /**
     * Distinct warning pattern for connection loss or vision failure
     */
    fun vibrateWarning() {
        val pattern = longArrayOf(0, 300, 150, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    /**
     * Strong pulsing pattern for emergency SOS
     */
    fun vibrateEmergency() {
        val pattern = longArrayOf(0, 400, 100, 400, 100, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    /**
     * Caution pulse for countdown steps
     */
    fun vibrateCaution() {
        vibrateNearby()
    }

    /**
     * Alert pulse for critical obstacle detection
     */
    fun vibrateAlert() {
        vibrateVeryClose()
    }
}
