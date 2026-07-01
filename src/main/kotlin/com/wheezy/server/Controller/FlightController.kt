package com.wheezy.server.Controller

import com.wheezy.server.DTO.FlightDTO
import com.wheezy.server.Models.LocationModel
import com.wheezy.server.Service.FlightService
import com.wheezy.server.Service.LocationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/flights")
class FlightController(
    private val flightService: FlightService,
    private val locationService: LocationService
) {

    private val logger = LoggerFactory.getLogger(FlightController::class.java)

    @GetMapping
    fun getAllFlights(): ResponseEntity<List<FlightDTO>> {
        return ResponseEntity.ok(flightService.getAllFlights())
    }

    @GetMapping("/{id}")
    fun getFlightById(@PathVariable id: Long): ResponseEntity<FlightDTO> {
        logger.info("🔍 GET /flights/$id")
        try {
            val flight = flightService.getFlightById(id)
            return if (flight != null) {
                logger.info("✅ Flight $id found: ${flight.airlineName}")
                ResponseEntity.ok(flight)
            } else {
                logger.warn("❌ Flight $id not found")
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
        } catch (e: Exception) {
            logger.error("Error getting flight $id: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/search")
    fun searchFlights(
        @RequestParam departureCity: String,
        @RequestParam arrivalCity: String,
        @RequestParam(required = false) flightDate: LocalDate?,
        @RequestParam(required = false) classType: String?
    ): ResponseEntity<List<FlightDTO>> {
        logger.info("🔍 Search flights: $departureCity → $arrivalCity, date=$flightDate, class=$classType")
        val result = if (flightDate != null) {
            if (classType != null) {
                flightService.findFlightsByCitiesAndDateAndClass(departureCity, arrivalCity, flightDate, classType)
            } else {
                flightService.findFlightsByCitiesAndDate(departureCity, arrivalCity, flightDate)
            }
        } else {
            if (classType != null) {
                flightService.findFlightsByCitiesAndClass(departureCity, arrivalCity, classType)
            } else {
                flightService.findFlightsByCities(departureCity, arrivalCity)
            }
        }
        logger.info("✅ Found ${result.size} flights")
        return ResponseEntity.ok(result)
    }

    @PostMapping
    fun createFlight(@RequestBody flightDTO: FlightDTO): ResponseEntity<FlightDTO> {
        logger.info("📝 Creating flight: ${flightDTO.airlineName}")
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.createFlight(flightDTO))
    }

    @PutMapping("/{id}")
    fun updateFlight(@PathVariable id: Long, @RequestBody flightDTO: FlightDTO): ResponseEntity<FlightDTO> {
        logger.info("📝 Updating flight $id: ${flightDTO.airlineName}")
        return ResponseEntity.ok(flightService.updateFlight(id, flightDTO))
    }

    @DeleteMapping("/{id}")
    fun deleteFlight(@PathVariable id: Long): ResponseEntity<Void> {
        logger.info("🗑️ Deleting flight $id")
        flightService.deleteFlight(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/locations")
    fun getLocations(): ResponseEntity<List<LocationModel>> {
        return ResponseEntity.ok(locationService.getAllLocations())
    }

    @GetMapping("/class-seats")
    fun getClassSeats(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(flightService.getAllClassSeats())
    }

    @DeleteMapping("/cache")
    fun clearCache(): ResponseEntity<Map<String, String>> {
        logger.info("🧹 Clearing all caches")
        flightService.clearCache()
        locationService.clearLocationsCache()
        return ResponseEntity.ok(mapOf("message" to "Cache cleared successfully"))
    }
}