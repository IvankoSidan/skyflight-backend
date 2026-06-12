package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "referrals")
data class Referral(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "referrer_id", nullable = false)
    val referrerId: Long,

    @Column(name = "referred_id", nullable = false)
    val referredId: Long,

    @Column(name = "referral_code", nullable = false)
    val referralCode: String,

    @Column(name = "discount_percent")
    val discountPercent: Int = 10,

    @Column(name = "discount_amount")
    val discountAmount: Long? = null,

    @Enumerated(EnumType.STRING)
    var status: ReferralStatus = ReferralStatus.PENDING,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ReferralStatus {
    PENDING, COMPLETED, EXPIRED
}
