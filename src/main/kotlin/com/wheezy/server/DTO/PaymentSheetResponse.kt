package com.wheezy.server.DTO

data class PaymentSheetResponse(
    val paymentIntentClientSecret: String,
    val ephemeralKey: String?,
    val customerId: String?,
    val publishableKey: String
)
