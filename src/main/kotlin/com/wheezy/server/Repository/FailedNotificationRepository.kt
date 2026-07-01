package com.wheezy.server.Repository

import com.wheezy.server.Models.FailedNotification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface FailedNotificationRepository : JpaRepository<FailedNotification, Long> {

    @Query("SELECT n FROM FailedNotification n WHERE n.status = 'PENDING' AND n.nextRetryAt <= :now ORDER BY n.nextRetryAt ASC")
    fun findPendingRetries(@Param("now") now: LocalDateTime, pageable: Pageable): List<FailedNotification>

    @Modifying
    @Transactional
    @Query("UPDATE FailedNotification n SET n.status = 'SUCCESS', n.processedAt = :processedAt WHERE n.id = :id")
    fun markAsSuccess(@Param("id") id: Long, @Param("processedAt") processedAt: LocalDateTime)

    @Modifying
    @Transactional
    @Query("UPDATE FailedNotification n SET n.status = 'FAILED', n.lastError = :error, n.processedAt = :processedAt WHERE n.id = :id")
    fun markAsFailed(@Param("id") id: Long, @Param("error") error: String, @Param("processedAt") processedAt: LocalDateTime)

    @Modifying
    @Transactional
    @Query("DELETE FROM FailedNotification n WHERE n.status IN ('SUCCESS', 'FAILED') AND n.processedAt <= :olderThan")
    fun deleteOldNotifications(@Param("olderThan") olderThan: LocalDateTime): Int
}