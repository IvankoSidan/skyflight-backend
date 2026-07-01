package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

data class FlightDTO(
    val flightId: Long? = null,
    val airlineLogo: String,
    val airlineName: String,
    val arriveTime: String,
    val classSeat: String,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
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
