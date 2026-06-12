package com.wheezy.server.DTO

import java.math.BigDecimal
import java.time.LocalDate

// Добавьте в FlightDTO.kt:

data class FlightDTO(
    val flightId: Long? = null,
    val airlineLogo: String,
    val airlineName: String,
    val arriveTime: String,
    val classSeat: String,
    val flightDate: LocalDate,
    val departureCity: String,
    val departureShort: String,
    val totalSeats: Int,
    val price: BigDecimal,
    val reservedSeats: String,
    val departureTime: String,
    val arrivalCity: String,
    val arrivalShort: String
) {
    val fullLogoUrl: String
        get() = "/api/logo/$airlineLogo"

    companion object {
        fun fromEntity(flight: com.wheezy.server.Models.Flight): FlightDTO {
            return FlightDTO(
                flightId = flight.flightId,
                airlineLogo = flight.airlineLogo,
                airlineName = flight.airlineName,
                arriveTime = flight.arriveTime,
                classSeat = flight.classSeat,
                flightDate = flight.flightDate,
                departureCity = flight.departureCity,
                departureShort = flight.departureShort,
                totalSeats = flight.totalSeats,
                price = flight.price,
                reservedSeats = flight.reservedSeats,
                departureTime = flight.departureTime,
                arrivalCity = flight.arrivalCity,
                arrivalShort = flight.arrivalShort
            )
        }
    }
}
