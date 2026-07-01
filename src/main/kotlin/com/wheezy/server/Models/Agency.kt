package com.wheezy.server.Models

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "agencies")
data class Agency(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = true)
    var slug: String,

    @Column(nullable = false, unique = true)
    var email: String,

    var phone: String? = null,
    var address: String? = null,
    var logoUrl: String? = null,
    var website: String? = null,

    @Column(name = "subscription_plan")
    var subscriptionPlan: String = "FREE",

    @Column(name = "subscription_valid_until")
    var subscriptionValidUntil: LocalDateTime? = null,

    @Column(name = "max_users")
    var maxUsers: Int = 10,

    @Column(name = "max_bookings_per_month")
    var maxBookingsPerMonth: Int = 100,

    @Column(name = "commission_percent")
    var commissionPercent: BigDecimal = BigDecimal(10.00),

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)