package com.wheezy.server.Enums

enum class PaymentStatus {
    PENDING,
    REQUIRES_PAYMENT_METHOD,
    SUCCEEDED,
    FAILED,
    CANCELED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    REFUND_FAILED,
}
