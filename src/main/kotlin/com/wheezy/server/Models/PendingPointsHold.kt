package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "pending_points_holds")
data class PendingPointsHold(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "booking_id", nullable = false)
    val bookingId: Long,

    @Column(name = "points_held", nullable = false)
    val pointsHeld: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(30),

    @Column(name = "status", nullable = false)
    var status: String = "ACTIVE"
)