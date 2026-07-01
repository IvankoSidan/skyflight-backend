package com.wheezy.server.Repository

import com.wheezy.server.Models.Agency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface AgencyRepository : JpaRepository<Agency, Long> {

    fun findBySlug(slug: String): Optional<Agency>

    fun findByEmail(email: String): Optional<Agency>

    @Query("SELECT a FROM Agency a WHERE a.subscriptionPlan = :plan AND a.isActive = true")
    fun findAllBySubscriptionPlan(@Param("plan") plan: String): List<Agency>

    @Query("SELECT COUNT(u) FROM User u WHERE u.agencyId = :agencyId")
    fun countUsersByAgencyId(@Param("agencyId") agencyId: Long): Long

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.agencyId = :agencyId AND b.bookingDate BETWEEN :start AND :end")
    fun countBookingsByAgencyIdAndDateRange(
        @Param("agencyId") agencyId: Long,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): Long

    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM Booking b WHERE b.agencyId = :agencyId AND b.status = 'CONFIRMED'")
    fun sumRevenueByAgencyId(@Param("agencyId") agencyId: Long): Double
}