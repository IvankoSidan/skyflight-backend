package com.wheezy.server.DTO

data class SavedCardResponse(
    val id: Long,
    val stripePaymentMethodId: String,
    val cardLast4: String,
    val cardBrand: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val isDefault: Boolean
)

data class SetDefaultCardRequest(
    val paymentMethodId: String
)