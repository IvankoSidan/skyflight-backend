package com.wheezy.server.Models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.math.BigDecimal
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

    @Column(name = "google_id")
    val googleId: String? = null,

    val name: String? = null,
    val profilePicture: String? = null,

    @Column(name = "is_enabled")
    val isEnabled: Boolean = true,

    @Column(name = "agency_id")
    var agencyId: Long? = null,

    var role: String = "CUSTOMER",

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "stripe_customer_id")
    var stripeCustomerId: String? = null,

    @Column(name = "country_code")
    var countryCode: String? = null,

    @Column(name = "tax_rate")
    var taxRate: BigDecimal? = null
) {
    fun isAgencyUser(): Boolean = agencyId != null && role != "CUSTOMER"
    fun isAgencyOwner(): Boolean = role == "OWNER"
    fun isAgencyAdmin(): Boolean = role == "OWNER" || role == "ADMIN"
    fun hasPermission(permission: String): Boolean = when (role) {
        "OWNER" -> true
        "ADMIN" -> permission in listOf("manage_users", "manage_bookings", "view_reports", "manage_settings")
        "MANAGER" -> permission in listOf("manage_bookings", "view_reports")
        "AGENT" -> permission == "create_bookings"
        else -> false
    }
}