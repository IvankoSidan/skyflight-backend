package com.wheezy.server.Repository

import com.wheezy.server.Models.Flight
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface FlightRepository : JpaRepository<Flight, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flight f WHERE f.flightId = :flightId")
    fun findByIdWithPessimisticLock(@Param("flightId") flightId: Long): Flight?

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