package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "saved_cards")
data class SavedCard(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "stripe_payment_method_id", nullable = false, unique = true)
    val stripePaymentMethodId: String,

    @Column(name = "card_last4", nullable = false, length = 4)
    val cardLast4: String,

    @Column(name = "card_brand", nullable = false, length = 20)
    val cardBrand: String,

    @Column(name = "expiry_month", nullable = false)
    val expiryMonth: Int,

    @Column(name = "expiry_year", nullable = false)
    val expiryYear: Int,

    @Column(name = "is_default")
    var isDefault: Boolean = false,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
