package com.wheezy.server.Models

import com.fasterxml.jackson.annotation.JsonFormat
import com.wheezy.server.Enums.PaymentStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payments_user_id", columnList = "user_id"),
        Index(name = "idx_payments_booking_id", columnList = "booking_id"),
        Index(name = "idx_payments_stripe_payment_id", columnList = "stripe_payment_id", unique = true),
        Index(name = "idx_payments_provider_payment_id", columnList = "provider_payment_id", unique = true),
        Index(name = "idx_payments_status", columnList = "status"),
        Index(name = "idx_payments_created_at", columnList = "created_at"),
        Index(name = "idx_payments_flight_id", columnList = "flight_id"),
        Index(name = "idx_payments_promocode_id", columnList = "promocode_id")
    ]
)
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "booking_id", nullable = false)
    val bookingId: Long,

    @Column(name = "flight_id", nullable = false)
    val flightId: Long,

    @Column(nullable = false)
    var amount: Long,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Column(name = "provider_payment_id", nullable = false, unique = true)
    var providerPaymentId: String,

    @Enumerated(EnumType.STRING)
    var status: PaymentStatus,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "remember_card", nullable = false)
    var rememberCard: Boolean = false,

    @Column(name = "stripe_payment_id", unique = true)
    var stripePaymentId: String? = null,

    @Column(name = "refund_id")
    var refundId: String? = null,

    @Column(name = "failure_code", length = 50)
    var failureCode: String? = null,

    @Column(name = "failure_message", length = 500)
    var failureMessage: String? = null,

    @Column(name = "promocode_id")
    var promocodeId: Long? = null
)