package com.wheezy.server.Repository

import com.wheezy.server.Models.PendingPointsHold
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface PendingPointsHoldRepository : JpaRepository<PendingPointsHold, Long> {

    fun findByBookingIdAndStatus(bookingId: Long, status: String): PendingPointsHold?

    fun findByUserIdAndStatus(userId: Long, status: String): List<PendingPointsHold>

    @Modifying
    @Transactional
    @Query("UPDATE PendingPointsHold h SET h.status = 'EXPIRED' WHERE h.status = 'ACTIVE' AND h.expiresAt < :now")
    fun expireOldHolds(now: LocalDateTime): Int

    @Modifying
    @Transactional
    @Query("DELETE FROM PendingPointsHold h WHERE h.status IN ('RELEASED', 'CANCELLED', 'EXPIRED') AND h.createdAt < :cutoff")
    fun deleteOldHolds(@Param("cutoff") cutoff: LocalDateTime): Int
}