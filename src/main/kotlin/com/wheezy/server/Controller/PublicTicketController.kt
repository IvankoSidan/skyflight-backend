package com.wheezy.server.Controller

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.UserRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/ticket")
class PublicTicketController(
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val userRepository: UserRepository
) {

    @GetMapping("/{bookingId}")
    fun publicTicketPage(@PathVariable bookingId: Long, model: Model): String {
        try {
            val booking = bookingRepository.findById(bookingId).orElse(null)
            if (booking == null) {
                model.addAttribute("error", "Booking not found")
                model.addAttribute("errorCode", "404")
                return "ticket-error"
            }

            val flight = flightRepository.findById(booking.flightId).orElse(null)
            if (flight == null) {
                model.addAttribute("error", "Flight not found")
                model.addAttribute("errorCode", "404")
                return "ticket-error"
            }

            val user = userRepository.findById(booking.userId).orElse(null)

            model.addAttribute("bookingId", booking.id)
            model.addAttribute("airlineName", flight.airlineName)
            model.addAttribute("airlineLogo", flight.airlineLogo)
            model.addAttribute("departureCity", flight.departureCity)
            model.addAttribute("arrivalCity", flight.arrivalCity)
            model.addAttribute("flightDate", flight.flightDate.toString())
            model.addAttribute("departureTime", flight.departureTime)
            model.addAttribute("arriveTime", flight.arriveTime)
            model.addAttribute("seatNumbers", booking.seatNumbers)
            model.addAttribute("status", booking.status.name.lowercase())
            model.addAttribute("statusDisplay", when (booking.status) {
                BookingStatus.CONFIRMED -> "Confirmed"
                BookingStatus.PAID -> "Paid"
                BookingStatus.CANCELED -> "Cancelled"
                BookingStatus.PENDING_PAYMENT -> "Pending Payment"
                BookingStatus.FAILED -> "Failed"
                BookingStatus.UNPAID -> "Unpaid"
            })
            model.addAttribute("classSeat", flight.classSeat)
            model.addAttribute("price", flight.price)
            model.addAttribute("passengerName", user?.name ?: "Guest")
            model.addAttribute("passengerEmail", user?.email ?: "")

            return "ticket-public"
        } catch (e: Exception) {
            model.addAttribute("error", "Failed to load ticket: ${e.message}")
            model.addAttribute("errorCode", "500")
            return "ticket-error"
        }
    }
}