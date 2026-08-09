package com.visionmate.pro.model

enum class AppLanguage(val code: String, val displayName: String, val ttsLocaleTag: String) {
    ENGLISH("en", "English", "en-US"),
    TAMIL("ta", "தமிழ்", "ta-IN"),
    HINDI("hi", "हिन्दी", "hi-IN")
}

data class LanguageState(
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH
)
