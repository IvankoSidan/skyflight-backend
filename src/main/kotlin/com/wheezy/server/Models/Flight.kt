package com.wheezy.server.Models

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "flights")
data class Flight(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val flightId: Long = 0,

    @Column(name = "airline_logo", nullable = false)
    val airlineLogo: String,

    @Column(name = "airline_name", nullable = false)
    val airlineName: String,

    @Column(name = "arrive_time", nullable = false)
    val arriveTime: String,

    @Column(name = "class_seat", nullable = false)
    val classSeat: String,

    @Column(name = "flight_date", nullable = false)
    val flightDate: LocalDate,

    @Column(name = "departure_city", nullable = false)
    val departureCity: String,

    @Column(name = "departure_short", nullable = false, length = 10)
    val departureShort: String,

    @Column(name = "total_seats", nullable = false)
    val totalSeats: Int,

    @Column(nullable = false)
    val price: BigDecimal,

    @Column(name = "reserved_seats", columnDefinition = "TEXT")
    var reservedSeats: String = "",

    @Column(name = "departure_time", nullable = false)
    val departureTime: String,

    @Column(name = "arrival_city", nullable = false)
    val arrivalCity: String,

    @Column(name = "arrival_short", nullable = false, length = 10)
    val arrivalShort: String
)