package com.wheezy.server.Repository

import com.wheezy.server.Models.StripeWebhookEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface StripeWebhookEventRepository : JpaRepository<StripeWebhookEvent, String> {
    @Query("SELECT e FROM StripeWebhookEvent e WHERE e.processed = false AND e.retryCount < :maxRetries AND e.createdAt > :cutoff")
    fun findPendingRetries(@Param("maxRetries") maxRetries: Int, @Param("cutoff") cutoff: LocalDateTime): List<StripeWebhookEvent>

    @Modifying
    @Transactional
    fun deleteByCreatedAtBefore(cutoff: LocalDateTime): Int

    @Query("SELECT COUNT(e) FROM StripeWebhookEvent e WHERE e.processed = false AND e.createdAt < :cutoff")
    fun countStaleEvents(@Param("cutoff") cutoff: LocalDateTime): Long
}
