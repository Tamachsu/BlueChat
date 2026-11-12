package com.example.bluechat.model

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: String,
    val isSent: Boolean,
    var status: String = "✓"
)
