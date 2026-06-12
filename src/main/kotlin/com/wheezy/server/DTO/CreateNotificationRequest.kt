package com.wheezy.server.DTO

import jakarta.validation.constraints.NotBlank

data class CreateNotificationRequest(
    @field:NotBlank(message = "Message cannot be empty")
    val message: String,
    val isRead: Boolean? = false
)

