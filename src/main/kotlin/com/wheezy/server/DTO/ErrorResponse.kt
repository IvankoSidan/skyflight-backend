package com.wheezy.server.DTO

import java.time.LocalDateTime

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: String? = null,
    val path: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
