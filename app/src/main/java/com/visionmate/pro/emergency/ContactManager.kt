package com.visionmate.pro.emergency

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmergencyContact(
    val name: String = "Guardian Contact",
    val phoneNumber: String = "+919094741350"
)

class ContactManager {

    private val _contact = MutableStateFlow(EmergencyContact())
    val contact: StateFlow<EmergencyContact> = _contact.asStateFlow()

    fun updateContact(name: String, phoneNumber: String) {
        _contact.value = EmergencyContact(name, phoneNumber)
    }
}
