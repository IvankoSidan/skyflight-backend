package com.wheezy.server.DTO

data class MassNotificationRequest(
    val title: String,
    val message: String,
    val target: String = "all",
    val imageUrl: String? = null,
    val actionUrl: String? = null
)

data class MassNotificationResponse(
    val success: Boolean,
    val message: String,
    val recipientsCount: Int? = null,
    val failedCount: Int? = null
)
