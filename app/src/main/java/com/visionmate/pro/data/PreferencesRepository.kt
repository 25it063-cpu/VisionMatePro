package com.visionmate.pro.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.visionmate.pro.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserPreferences(
    val cautionDistanceCm: Int = 150,
    val criticalDistanceCm: Int = 30,
    val emergencyContactName: String = "Guardian Contact",
    val emergencyContactPhone: String = "+919094741350",
    val isHapticsEnabled: Boolean = true,
    val isTtsEnabled: Boolean = true,
    val languageCode: String = "en",
    val cameraStreamUrl: String = "http://192.168.0.9:81/stream"
)

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vision_mate_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        return UserPreferences(
            cautionDistanceCm = prefs.getInt("caution_dist", 150),
            criticalDistanceCm = prefs.getInt("critical_dist", 30),
            emergencyContactName = prefs.getString("emergency_name", "Guardian Contact") ?: "Guardian Contact",
            emergencyContactPhone = prefs.getString("emergency_phone", "+919094741350") ?: "+919094741350",
            isHapticsEnabled = prefs.getBoolean("haptics_enabled", true),
            isTtsEnabled = prefs.getBoolean("tts_enabled", true),
            languageCode = prefs.getString("language_code", "en") ?: "en",
            cameraStreamUrl = prefs.getString("camera_url", "http://192.168.0.9:81/stream") ?: "http://192.168.0.9:81/stream"
        )
    }

    fun updateLanguage(language: AppLanguage) {
        prefs.edit {
            putString("language_code", language.code)
        }
        _preferences.value = _preferences.value.copy(languageCode = language.code)
    }

    fun updateDistances(cautionCm: Int, criticalCm: Int) {
        prefs.edit {
            putInt("caution_dist", cautionCm)
            putInt("critical_dist", criticalCm)
        }
        _preferences.value = _preferences.value.copy(
            cautionDistanceCm = cautionCm,
            criticalDistanceCm = criticalCm
        )
    }

    fun updateEmergencyContact(name: String, phone: String) {
        prefs.edit {
            putString("emergency_name", name)
            putString("emergency_phone", phone)
        }
        _preferences.value = _preferences.value.copy(
            emergencyContactName = name,
            emergencyContactPhone = phone
        )
    }

    fun updateCameraUrl(url: String) {
        prefs.edit {
            putString("camera_url", url)
        }
        _preferences.value = _preferences.value.copy(cameraStreamUrl = url)
    }
}
