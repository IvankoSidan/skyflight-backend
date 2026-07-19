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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy, HH:mm", locale = "en_US")
    val bookingDate: LocalDateTime,

    val flightId: Long,
    val airlineName: String,
    val airlineLogo: String,
    val departureCity: String,
    val arrivalCity: String,
    val departureTime: String,
    val arriveTime: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy", locale = "en_US")
    val flightDate: LocalDate,

    val classSeat: String,
    val price: BigDecimal,
    val paidAmount: BigDecimal? = null,
    val promocodeId: Long? = null,
    val promocodeCode: String? = null,
    val promocodeDiscountPercent: Int? = null,
    val promocodeDiscountAmount: Long? = null,
    val passengerName: String? = null,
    val passengerEmail: String? = null,
    val paymentMethod: String? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy, HH:mm", locale = "en_US")
    val paymentDate: LocalDateTime? = null,

    val paymentStatus: String? = null,
    val paymentAmount: BigDecimal? = null,
    val refundStatus: String? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy, HH:mm", locale = "en_US")
    val refundDate: LocalDateTime? = null,

    val refundAmount: BigDecimal? = null,
    val invoiceUrl: String? = null,
    val ticketUrl: String? = null,
    val totalPrice: BigDecimal? = null,
    val discountAmount: BigDecimal? = null,
    val finalPrice: BigDecimal? = null
)