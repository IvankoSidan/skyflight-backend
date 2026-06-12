package com.wheezy.server.Repository

import com.wheezy.server.Models.PointsTransaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface PointsTransactionRepository : JpaRepository<PointsTransaction, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<PointsTransaction>

    fun findByUserIdAndType(userId: Long, type: String): List<PointsTransaction>

    fun findByUserIdAndTypeAndCreatedAtAfter(
        userId: Long,
        type: String,
        createdAt: LocalDateTime
    ): List<PointsTransaction>

    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.type IN ('BOOKING', 'REVIEW', 'REFERRAL', 'MONTHLY_BONUS', 'WELCOME_BONUS') AND pt.amount > 0 AND pt.createdAt < :cutoff")
    fun findEarnedPointsOlderThan(@Param("cutoff") cutoff: LocalDateTime): List<PointsTransaction>

    @Query("SELECT SUM(pt.amount) FROM PointsTransaction pt WHERE pt.userId = :userId AND pt.type IN ('BOOKING', 'REVIEW', 'REFERRAL', 'MONTHLY_BONUS', 'WELCOME_BONUS') AND pt.amount > 0")
    fun getTotalEarnedPoints(@Param("userId") userId: Long): Int?

    @Query("SELECT SUM(pt.amount) FROM PointsTransaction pt WHERE pt.userId = :userId AND pt.type = 'REDEMPTION' AND pt.amount < 0")
    fun getTotalRedeemedPoints(@Param("userId") userId: Long): Int?

    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.userId = :userId AND pt.createdAt BETWEEN :start AND :end")
    fun findByUserIdAndDateRange(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<PointsTransaction>
}
