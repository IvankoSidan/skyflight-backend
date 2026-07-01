package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime
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