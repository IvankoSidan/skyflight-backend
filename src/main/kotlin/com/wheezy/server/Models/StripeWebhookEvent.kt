package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "stripe_webhook_events",
    indexes = [
        Index(name = "idx_webhook_processed", columnList = "processed"),
        Index(name = "idx_webhook_created_at", columnList = "created_at"),
        Index(name = "idx_webhook_type_processed", columnList = "type, processed"),
        Index(name = "idx_webhook_event_id", columnList = "event_id", unique = true)
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_webhook_event_id", columnNames = ["event_id"])
    ]
)
data class StripeWebhookEvent(
    @Id
    @Column(name = "event_id", nullable = false, length = 255)
    val eventId: String,

    @Column(name = "type", nullable = false, length = 100)
    val type: String,

    @Column(name = "processed", nullable = false)
    var processed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "payload", columnDefinition = "TEXT")
    var payload: String? = null,

    @Column(name = "payment_intent_id", length = 255)
    var paymentIntentId: String? = null,

    @Column(name = "booking_id")
    var bookingId: Long? = null
)
