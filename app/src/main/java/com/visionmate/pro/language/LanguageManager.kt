package com.visionmate.pro.language

import com.visionmate.pro.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LanguageManager {

    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
    }

    fun setLanguageByCode(code: String) {
        val matched = AppLanguage.values().firstOrNull { 
            it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) 
        }
        if (matched != null) {
            _currentLanguage.value = matched
        }
    }
}
