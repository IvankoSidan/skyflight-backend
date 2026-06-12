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
class FlightService(private val flightRepository: FlightRepository) {

    private val logger = LoggerFactory.getLogger(FlightService::class.java)

    // Получить все рейсы
    @Transactional(readOnly = true)
    fun getAllFlights(): List<FlightDTO> {
        return flightRepository.findAll().map { it.toDTO() }
    }

    // Получить рейс по ID
    @Transactional(readOnly = true)
    @Cacheable(value = ["flight"], key = "#id", unless = "#result == null")
    fun getFlightById(id: Long): FlightDTO {
        logger.info("✈️ Loading flight from DATABASE (cache miss): id=$id")
        val flight = flightRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Flight with ID $id not found") }
        return flight.toDTO()
    }

    // Поиск рейсов по городам (с кэшированием)
    @Transactional(readOnly = true)
    @Cacheable(value = ["flights"], key = "#departureCity + ':' + #arrivalCity", unless = "#result == null || #result.isEmpty()")
    fun findFlightsByCities(departureCity: String, arrivalCity: String): List<FlightDTO> {
        logger.info("🔍 Searching flights from DATABASE (cache miss): $departureCity → $arrivalCity")
        return flightRepository.findByDepartureCityAndArrivalCity(departureCity, arrivalCity)
            .map { it.toDTO() }
    }

    // Поиск рейсов по дате
    @Transactional(readOnly = true)
    @Cacheable(value = ["flights_date"], key = "#flightDate", unless = "#result == null || #result.isEmpty()")
    fun findFlightsByDate(flightDate: LocalDate): List<FlightDTO> {
        logger.info("📅 Loading flights by date from DATABASE (cache miss): $flightDate")
        return flightRepository.findByFlightDate(flightDate).map { it.toDTO() }
    }

    // Поиск рейсов по городам и дате (с кэшированием)
    @Transactional(readOnly = true)
    @Cacheable(value = ["flights"], key = "#departureCity + ':' + #arrivalCity + ':' + #flightDate", unless = "#result == null || #result.isEmpty()")
    fun findFlightsByCitiesAndDate(
        departureCity: String,
        arrivalCity: String,
        flightDate: LocalDate
    ): List<FlightDTO> {
        logger.info("🔍 Searching flights from DATABASE (cache miss): $departureCity → $arrivalCity on $flightDate")
        return flightRepository.findByDepartureCityAndArrivalCityAndFlightDate(
            departureCity,
            arrivalCity,
            flightDate
        ).map { it.toDTO() }
    }

    // Поиск с классом
    @Transactional(readOnly = true)
    fun findFlightsByCitiesAndClass(departureCity: String, arrivalCity: String, classType: String?): List<FlightDTO> {
        val flights = flightRepository.findByDepartureCityAndArrivalCity(departureCity, arrivalCity)
        return flights
            .filter { classType == null || it.classSeat.equals(classType, ignoreCase = true) }
            .map { it.toDTO() }
    }

    @Transactional(readOnly = true)
    fun findFlightsByCitiesAndDateAndClass(departureCity: String, arrivalCity: String, flightDate: LocalDate, classType: String?): List<FlightDTO> {
        val flights = flightRepository.findByDepartureCityAndArrivalCityAndFlightDate(departureCity, arrivalCity, flightDate)
        return flights
            .filter { classType == null || it.classSeat.equals(classType, ignoreCase = true) }
            .map { it.toDTO() }
    }

    // Создать новый рейс (очищаем кэш)
    @Transactional
    @CacheEvict(value = ["flights", "flights_date", "locations"], allEntries = true)
    fun createFlight(flightDTO: FlightDTO): FlightDTO {
        logger.info("✈️ Creating new flight, clearing cache")
        val flight = flightDTO.toEntity()
        return flightRepository.save(flight).toDTO()
    }

    // Обновить рейс (очищаем кэш)
    @Transactional
    @CacheEvict(value = ["flights", "flights_date", "flight"], allEntries = true)
    fun updateFlight(id: Long, flightDTO: FlightDTO): FlightDTO {
        logger.info("✈️ Updating flight id=$id, clearing cache")
        val existingFlight = flightRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Flight with ID $id not found") }
        val updatedFlight = flightDTO.toEntity(existingFlight.flightId)
        return flightRepository.save(updatedFlight).toDTO()
    }

    // Удалить рейс (очищаем кэш)
    @Transactional
    @CacheEvict(value = ["flights", "flights_date", "flight"], allEntries = true)
    fun deleteFlight(id: Long) {
        logger.info("🗑️ Deleting flight id=$id, clearing cache")
        if (!flightRepository.existsById(id)) {
            throw ResourceNotFoundException("Flight with ID $id not found")
        }
        flightRepository.deleteById(id)
    }

    fun getAllClassSeats(): List<String> {
        return flightRepository.findDistinctClassSeats()
    }

    // Преобразование Flight в FlightDTO
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

    // Преобразование FlightDTO в Flight
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
        return path.substringAfterLast('/').substringBefore('?')
    }
}
