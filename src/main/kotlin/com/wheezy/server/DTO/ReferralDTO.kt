package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat

data class ReferralCodeResponse(
    val code: String,
    val usageCount: Int,
    val maxUses: Int,
    val isValid: Boolean,
    val shareLink: String
)

data class ReferralApplyRequest(
    val code: String
)

data class ReferralApplyResponse(
    val success: Boolean,
    val message: String,
    val discountPercent: Int? = null,
    val discountAmount: Long? = null
)

data class ReferralInfoResponse(
    val myCode: String,
    val myReferrals: List<ReferredUser>,
    val totalReferrals: Int,
    val totalDiscountEarned: Long
)

data class ReferredUser(
    val email: String,
    val name: String?,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy, HH:mm", locale = "en_US")
    val registeredAt: String,

    val status: String
)