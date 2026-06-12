package com.wheezy.server.Repository

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Models.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface BookingRepository : JpaRepository<Booking, Long> {
    fun findByFlightId(flightId: Long): List<Booking>
    fun findByFlightIdAndStatusIn(flightId: Long, statuses: List<String>): List<Booking>
    fun findByUserId(userId: Long): List<Booking>
    fun deleteByStatusAndCanceledAtBefore(status: BookingStatus, dateTime: LocalDateTime)
}
