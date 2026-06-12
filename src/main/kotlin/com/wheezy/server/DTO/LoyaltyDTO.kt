package com.wheezy.server.DTO

data class PointsBalanceResponse(
    val balance: Int,
    val lifetimePoints: Int,
    val tier: String,
    val nextTier: String?,
    val pointsToNextTier: Int,
    val cashbackPercent: Int
)

data class PointsTransactionResponse(
    val id: Long,
    val amount: Int,
    val type: String,
    val description: String?,
    val createdAt: String
)

data class RedeemPointsRequest(
    val points: Int,
    val bookingId: Long
)

data class RedeemPointsResponse(
    val success: Boolean,
    val discountAmount: Long,
    val remainingPoints: Int,
    val message: String
)

data class CalculateDiscountRequest(
    val amount: Long,
    val points: Int
)

data class CalculateDiscountResponse(
    val discountAmount: Long,
    val pointsUsed: Int,
    val finalAmount: Long
)
