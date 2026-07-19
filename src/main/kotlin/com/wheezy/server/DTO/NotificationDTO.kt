package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class NotificationDTO(
    val message: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy, HH:mm", locale = "en_US")
    val timestamp: LocalDateTime,

    val isRead: Boolean
)