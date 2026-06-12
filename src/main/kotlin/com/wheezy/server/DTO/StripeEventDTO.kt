package com.wheezy.server.DTO

data class StripeEventDTO(
    val type: String,
    val paymentId: Long
)
