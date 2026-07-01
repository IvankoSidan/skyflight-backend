package com.wheezy.server.DTO

data class PaymentSheetRequest(
    val bookingId: Long,
    val amount: Long,
    val currency: String,
    val promocodeId: Long? = null,
    val saveCard: Boolean = false
)