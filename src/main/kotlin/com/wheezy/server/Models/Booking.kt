package com.wheezy.server.Models

import com.fasterxml.jackson.annotation.JsonFormat
import com.wheezy.server.Enums.BookingStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "bookings")
data class Booking(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "agency_id", nullable = false)
    val agencyId: Long,

    @Column(name = "flight_id", nullable = false)
    val flightId: Long,

    @Column(name = "seat_count")
    val seatCount: Int = 1,

    @Column(name = "seat_numbers", nullable = false)
    val seatNumbers: String,

    @Enumerated(EnumType.STRING)
    var status: BookingStatus = BookingStatus.PENDING_PAYMENT,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "booking_date")
    val bookingDate: LocalDateTime = LocalDateTime.now(),

    var canceledAt: LocalDateTime? = null,

    @Column(name = "paid_amount")
    var paidAmount: BigDecimal? = null,

    @Column(name = "promocode_id")
    var promocodeId: Long? = null
)