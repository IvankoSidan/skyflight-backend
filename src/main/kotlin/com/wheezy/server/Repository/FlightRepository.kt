package com.wheezy.server.Repository

import com.wheezy.server.Models.Flight
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface FlightRepository : JpaRepository<Flight, Long> {
    fun findByDepartureCityAndArrivalCity(departureCity: String, arrivalCity: String): List<Flight>

    fun findByFlightDate(flightDate: LocalDate): List<Flight>

    fun findByDepartureCityAndArrivalCityAndFlightDate(
        departureCity: String,
        arrivalCity: String,
        flightDate: LocalDate
    ): List<Flight>

    @Query("SELECT DISTINCT f.classSeat FROM Flight f")
    fun findDistinctClassSeats(): List<String>
}
