package com.yasmin.ai.data

data class ChatMessage(
    val id: Long,
    val role: String, // "user" or "assistant"
    val content: String,
    val createdAt: Long
)

class LlmException(message: String) : Exception(message)
