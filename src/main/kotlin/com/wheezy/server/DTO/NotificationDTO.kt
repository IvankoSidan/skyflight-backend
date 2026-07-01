package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class NotificationDTO(
    val message: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime,

    val isRead: Boolean
)