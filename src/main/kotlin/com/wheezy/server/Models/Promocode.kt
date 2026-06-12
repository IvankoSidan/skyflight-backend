package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "promocodes")
data class Promocode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val code: String,

    @Column(name = "discount_percent")
    val discountPercent: Int? = null,

    @Column(name = "discount_amount")
    val discountAmount: Long? = null,

    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDateTime,

    @Column(name = "valid_until", nullable = false)
    val validUntil: LocalDateTime,

    @Column(name = "max_uses")
    val maxUses: Int? = null,

    @Column(name = "used_count")
    var usedCount: Int = 0,

    @Column(name = "min_order_amount")
    val minOrderAmount: Long? = null,

    @Column(name = "is_active")
    val isActive: Boolean = true,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
