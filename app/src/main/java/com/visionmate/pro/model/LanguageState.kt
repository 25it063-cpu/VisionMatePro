package com.visionmate.pro.model

enum class AppLanguage(val code: String, val displayName: String, val ttsLocaleTag: String) {
    ENGLISH("en", "English", "en-IN"),
    TAMIL("ta", "தமிழ்", "ta-IN"),
    HINDI("hi", "हिन्दी", "hi-IN"),
    TELUGU("te", "తెలుగు", "te-IN"),
    MALAYALAM("ml", "മലയാളം", "ml-IN")
}

data class LanguageState(
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH
)
