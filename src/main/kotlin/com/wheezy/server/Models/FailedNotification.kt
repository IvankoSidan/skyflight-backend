package com.wheezy.server.Models

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "failed_notifications")
data class FailedNotification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "body", nullable = false)
    val body: String,

    @Column(name = "data", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    var data: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "max_retries", nullable = false)
    val maxRetries: Int = 5,

    @Column(name = "last_error", length = 500)
    var lastError: String? = null,

    @Column(name = "status", nullable = false)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "next_retry_at", nullable = false)
    var nextRetryAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null
)