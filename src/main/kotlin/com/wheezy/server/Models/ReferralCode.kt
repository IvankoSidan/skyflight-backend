package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "referral_codes")
data class ReferralCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "code", nullable = false, unique = true)
    val code: String,

    @Column(name = "usage_count")
    var usageCount: Int = 0,

    @Column(name = "max_uses")
    var maxUses: Int = 10,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
) {
    fun isValid(): Boolean = (expiresAt == null || LocalDateTime.now().isBefore(expiresAt)) && usageCount < maxUses
}
