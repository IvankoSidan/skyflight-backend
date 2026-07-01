package com.wheezy.server.DTO

import java.math.BigDecimal

data class AgencyCreateRequest(
    val name: String,
    val email: String
)

data class AgencyResponse(
    val id: Long,
    val name: String,
    val slug: String,
    val email: String,
    val subscriptionPlan: String
)

data class AgencyInfoResponse(
    val id: Long,
    val name: String,
    val slug: String,
    val email: String,
    val subscriptionPlan: String,
    val maxUsers: Int,
    val currentUsers: Int,
    val maxBookingsPerMonth: Int,
    val commissionPercent: BigDecimal,
    val limitsOk: Boolean,
    val isSubscriptionValid: Boolean
)

data class AgencyStatisticsResponse(
    val totalBookings: Long,
    val totalRevenue: Double,
    val activeUsers: Int,
    val commissionEarned: Double
)

data class AgencyInviteRequest(
    val email: String,
    val role: String
)