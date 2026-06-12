package com.wheezy.server.Controller

import com.wheezy.server.DTO.FlightDTO
import com.wheezy.server.Models.LocationModel
import com.wheezy.server.Service.FlightService
import com.wheezy.server.Service.LocationService
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

    @GetMapping
    fun getAllFlights(): ResponseEntity<List<FlightDTO>> {
        return ResponseEntity.ok(flightService.getAllFlights())
    }

    @GetMapping("/{id}")
    fun getFlightById(@PathVariable id: Long): ResponseEntity<FlightDTO> {
        return ResponseEntity.ok(flightService.getFlightById(id))
    }

    @GetMapping("/search")
    fun searchFlights(
        @RequestParam departureCity: String,
        @RequestParam arrivalCity: String,
        @RequestParam(required = false) flightDate: LocalDate?,
        @RequestParam(required = false) classType: String?
    ): ResponseEntity<List<FlightDTO>> {
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
        return ResponseEntity.ok(result)
    }

    @PostMapping
    fun createFlight(@RequestBody flightDTO: FlightDTO): ResponseEntity<FlightDTO> {
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.createFlight(flightDTO))
    }

    @PutMapping("/{id}")
    fun updateFlight(@PathVariable id: Long, @RequestBody flightDTO: FlightDTO): ResponseEntity<FlightDTO> {
        return ResponseEntity.ok(flightService.updateFlight(id, flightDTO))
    }

    @DeleteMapping("/{id}")
    fun deleteFlight(@PathVariable id: Long): ResponseEntity<Void> {
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

    // Эндпоинт для очистки кэша (только для администратора)
    @DeleteMapping("/cache")
    fun clearCache(): ResponseEntity<Map<String, String>> {
        flightService.deleteFlight(-1) // Хак для вызова @CacheEvict
        locationService.clearLocationsCache()
        return ResponseEntity.ok(mapOf("message" to "Cache cleared successfully"))
    }
}
