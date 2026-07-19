package com.wheezy.server.Service

import com.wheezy.server.DTO.FlightDTO
import com.wheezy.server.Exception.ResourceNotFoundException
import com.wheezy.server.Models.Flight
import com.wheezy.server.Repository.FlightRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class FlightService(
    private val flightRepository: FlightRepository,
    private val flightCacheManager: FlightCacheManager
) {

    private val logger = LoggerFactory.getLogger(FlightService::class.java)

    @Transactional(readOnly = true)
    fun getAllFlights(): List<FlightDTO> {
        return flightRepository.findAll().map { it.toDTO() }
    }

    fun getFlightById(id: Long): FlightDTO? {
        logger.info("Fetching flight by ID: $id")

        val cached = flightCacheManager.getFlight(id)
        if (cached != null) {
            logger.info("Flight $id found in cache")
            return cached
        }

        val flight = flightRepository.findById(id).orElse(null)
        if (flight == null) {
            logger.warn("Flight not found: $id")
            return null
        }

        val dto = flight.toDTO()
        flightCacheManager.putFlight(id, dto)
        logger.info("Flight $id cached")
        return dto
    }

    @Transactional(readOnly = true)
    fun findFlightsByCities(departureCity: String, arrivalCity: String): List<FlightDTO> {
        val key = "search:$departureCity:$arrivalCity"
        val cached = flightCacheManager.getFlights(key)
        if (cached != null) {
            return cached
        }

        val flights = flightRepository.findByDepartureCityAndArrivalCity(departureCity, arrivalCity)
            .map { it.toDTO() }

        flightCacheManager.putFlights(key, flights)
        return flights
    }

    @Transactional(readOnly = true)
    fun findFlightsByDate(flightDate: LocalDate): List<FlightDTO> {
        val key = "date:$flightDate"
        val cached = flightCacheManager.getFlights(key)
        if (cached != null) {
            return cached
        }

        val flights = flightRepository.findByFlightDate(flightDate).map { it.toDTO() }
        flightCacheManager.putFlights(key, flights)
        return flights
    }

    @Transactional(readOnly = true)
    fun findFlightsByCitiesAndDate(
        departureCity: String,
        arrivalCity: String,
        flightDate: LocalDate
    ): List<FlightDTO> {
        val key = "search:$departureCity:$arrivalCity:$flightDate"
        val cached = flightCacheManager.getFlights(key)
        if (cached != null) {
            return cached
        }

        val flights = flightRepository.findByDepartureCityAndArrivalCityAndFlightDate(
            departureCity,
            arrivalCity,
            flightDate
        ).map { it.toDTO() }

        flightCacheManager.putFlights(key, flights)
        return flights
    }

    @Transactional(readOnly = true)
    fun findFlightsByCitiesAndClass(
        departureCity: String,
        arrivalCity: String,
        classType: String?
    ): List<FlightDTO> {
        val key = "search:$departureCity:$arrivalCity:class:$classType"
        val cached = flightCacheManager.getFlights(key)
        if (cached != null) {
            return cached
        }

        val flights = flightRepository.findByDepartureCityAndArrivalCity(departureCity, arrivalCity)
            .filter { classType == null || it.classSeat.equals(classType, ignoreCase = true) }
            .map { it.toDTO() }

        flightCacheManager.putFlights(key, flights)
        return flights
    }

    @Transactional(readOnly = true)
    fun findFlightsByCitiesAndDateAndClass(
        departureCity: String,
        arrivalCity: String,
        flightDate: LocalDate,
        classType: String?
    ): List<FlightDTO> {
        val key = "search:$departureCity:$arrivalCity:$flightDate:class:$classType"
        val cached = flightCacheManager.getFlights(key)
        if (cached != null) {
            return cached
        }

        val flights = flightRepository.findByDepartureCityAndArrivalCityAndFlightDate(
            departureCity,
            arrivalCity,
            flightDate
        ).filter { classType == null || it.classSeat.equals(classType, ignoreCase = true) }
            .map { it.toDTO() }

        flightCacheManager.putFlights(key, flights)
        return flights
    }

    @Transactional
    fun createFlight(flightDTO: FlightDTO): FlightDTO {
        logger.info("Creating new flight, clearing cache")
        val flight = flightDTO.toEntity()
        val saved = flightRepository.save(flight)
        flightCacheManager.clearAll()
        return saved.toDTO()
    }

    @Transactional
    fun updateFlight(id: Long, flightDTO: FlightDTO): FlightDTO {
        logger.info("Updating flight id=$id, clearing cache")
        val existingFlight = flightRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Flight with ID $id not found") }
        val updatedFlight = flightDTO.toEntity(existingFlight.flightId)
        val saved = flightRepository.save(updatedFlight)
        flightCacheManager.clearAll()
        return saved.toDTO()
    }

    @Transactional
    fun deleteFlight(id: Long) {
        logger.info("Deleting flight id=$id, clearing cache")
        if (!flightRepository.existsById(id)) {
            throw ResourceNotFoundException("Flight with ID $id not found")
        }
        flightRepository.deleteById(id)
        flightCacheManager.clearAll()
    }

    fun getAllClassSeats(): List<String> {
        return flightRepository.findDistinctClassSeats()
    }

    fun clearCache() {
        logger.info("Flight cache cleared")
        flightCacheManager.clearAll()
    }

    private fun Flight.toDTO(): FlightDTO {
        return FlightDTO(
            flightId = this.flightId,
            airlineLogo = extractFilenameFromPath(this.airlineLogo),
            airlineName = this.airlineName,
            arriveTime = this.arriveTime,
            classSeat = this.classSeat,
            flightDate = this.flightDate,
            departureCity = this.departureCity,
            departureShort = this.departureShort,
            totalSeats = this.totalSeats,
            price = this.price,
            reservedSeats = this.reservedSeats,
            departureTime = this.departureTime,
            arrivalCity = this.arrivalCity,
            arrivalShort = this.arrivalShort
        )
    }

    private fun FlightDTO.toEntity(flightId: Long? = null): Flight {
        return Flight(
            flightId = flightId ?: 0,
            airlineLogo = extractFilenameFromPath(this.airlineLogo),
            airlineName = this.airlineName,
            arriveTime = this.arriveTime,
            classSeat = this.classSeat,
            flightDate = this.flightDate,
            departureCity = this.departureCity,
            departureShort = this.departureShort,
            totalSeats = this.totalSeats,
            price = this.price,
            reservedSeats = this.reservedSeats,
            departureTime = this.departureTime,
            arrivalCity = this.arrivalCity,
            arrivalShort = this.arrivalShort
        )
    }

    private fun extractFilenameFromPath(path: String): String {
        return path.substringAfterLast('/').substringBefore('?').substringBefore('.')
    }
}