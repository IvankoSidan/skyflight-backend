package com.wheezy.server.Models

import com.wheezy.server.Enums.RetryStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "payment_retry")
data class PaymentRetry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val paymentId: Long,
    val paymentIntentId: String,
    var retryCount: Int = 0,
    val maxRetries: Int = 3,
    var lastRetryAt: LocalDateTime? = null,
    var nextRetryAt: LocalDateTime? = null,
    @Enumerated(EnumType.STRING)
    var status: RetryStatus = RetryStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
