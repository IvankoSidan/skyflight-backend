package com.wheezy.server.Models

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "tax_rates")
data class TaxRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "country_code", nullable = false, unique = true, length = 2)
    val countryCode: String,

    @Column(name = "country_name", nullable = false, length = 100)
    val countryName: String,

    @Column(name = "tax_name", nullable = false, length = 50)
    val taxName: String,

    @Column(name = "tax_rate", nullable = false)
    val taxRate: BigDecimal,

    @Column(name = "is_default")
    val isDefault: Boolean = false,

    @Column(name = "is_active")
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
