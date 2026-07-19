package com.wheezy.server.Repository

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Models.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface BookingRepository : JpaRepository<Booking, Long> {
    fun findByFlightId(flightId: Long): List<Booking>
    fun findByFlightIdAndStatusIn(flightId: Long, statuses: List<String>): List<Booking>
    fun findByUserId(userId: Long): List<Booking>

    @Modifying
    @Transactional
    @Query("DELETE FROM Booking b WHERE b.status = :status AND b.canceledAt < :dateTime")
    fun deleteByStatusAndCanceledAtBefore(@Param("status") status: BookingStatus, @Param("dateTime") dateTime: LocalDateTime): Int

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.bookingDate < :dateTime")
    fun findByStatusAndBookingDateBefore(@Param("status") status: BookingStatus, @Param("dateTime") dateTime: LocalDateTime): List<Booking>
}