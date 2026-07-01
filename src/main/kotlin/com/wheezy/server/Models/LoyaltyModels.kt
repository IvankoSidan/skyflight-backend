package com.wheezy.server.Models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_points")
data class UserPoints(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    var balance: Int = 0,
    @Column(name = "lifetime_points")
    var lifetimePoints: Int = 0,
    var tier: String = "BRONZE",
    @Column(name = "frozen_balance")
    var frozenBalance: Int = 0,
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "points_transactions")
data class PointsTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id")
    val userId: Long,

    val amount: Int,
    val type: String,

    @Column(name = "reference_id")
    val referenceId: Long? = null,

    val description: String? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "tier_benefits")
data class TierBenefit(
    @Id
    val tier: String,
    @Column(name = "cashback_percent")
    val cashbackPercent: Int = 0,
    @Column(name = "free_seat_selection")
    val freeSeatSelection: Boolean = false,
    @Column(name = "priority_boarding")
    val priorityBoarding: Boolean = false,
    @Column(name = "free_baggage_kg")
    val freeBaggageKg: Int = 0,
    @Column(name = "min_points")
    val minPoints: Int = 0
)
