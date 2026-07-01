package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat
import com.wheezy.server.Enums.BookingStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class BookingDetailsDTO(
    val bookingId: Long,
    val seatNumbers: String,
    val seatCount: Int,
    val status: BookingStatus,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val bookingDate: LocalDateTime,

    val flightId: Long,
    val airlineName: String,
    val airlineLogo: String,
    val departureCity: String,
    val arrivalCity: String,
    val departureTime: String,
    val arriveTime: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val flightDate: LocalDate,

    val classSeat: String,
    val price: BigDecimal,
    val paidAmount: BigDecimal? = null,
    val promocodeId: Long? = null,
    val promocodeCode: String? = null,
    val promocodeDiscountPercent: Int? = null,
    val promocodeDiscountAmount: Long? = null
)