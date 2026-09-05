package com.visionmate.pro.model

enum class MessageRole {
    USER,
    ASSISTANT
}

data class Message(
    val role: MessageRole,
    val text: String
)
