package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = true)
    val password: String? = null,

    @Column(name = "google_id", nullable = true)
    val googleId: String? = null,

    @Column(nullable = true)
    val name: String? = null,

    @Column(name = "profile_picture", nullable = true)
    val profilePicture: String? = null,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "stripe_customer_id", nullable = true)
    var stripeCustomerId: String? = null
)
