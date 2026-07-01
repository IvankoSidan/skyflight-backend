package com.wheezy.server.DTO

data class PromocodeResponse(
    val id: Long?,
    val code: String,
    val discountPercent: Int?,
    val discountAmount: Long?,
    val discountedAmount: Long,
    val isValid: Boolean,
    val message: String?
)