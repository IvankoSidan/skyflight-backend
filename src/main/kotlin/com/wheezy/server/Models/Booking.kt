package com.wheezy.server.Models

import com.wheezy.server.Enums.BookingStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "bookings")
data class Booking(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "flight_id", nullable = false)
    val flightId: Long,

    @Column(name = "seat_count", nullable = false)
    val seatCount: Int = 1,

    @Column(name = "seat_numbers", nullable = false)
    val seatNumbers: String,

    @Enumerated(EnumType.STRING)
    var status: BookingStatus = BookingStatus.PENDING_PAYMENT,

    @Column(name = "booking_date", nullable = false)
    val bookingDate: LocalDateTime = LocalDateTime.now(),

    var canceledAt: LocalDateTime? = null,

    @Column(name = "paid_amount")
    var paidAmount: BigDecimal? = null,

    @Column(name = "promocode_id")
    var promocodeId: Long? = null
)
