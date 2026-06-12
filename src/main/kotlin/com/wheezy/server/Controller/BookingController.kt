package com.wheezy.server.Controller

import com.wheezy.server.DTO.BookingDetailsDTO
import com.wheezy.server.DTO.BookingRequestDTO
import com.wheezy.server.DTO.BookingResponseDTO
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Models.Booking
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.PaymentRepository
import com.wheezy.server.Repository.PromocodeRepository
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.NotificationSenderService
import com.wheezy.server.Service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.Principal
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/bookings")
class BookingController(
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository,
    private val flightRepository: FlightRepository,
    private val paymentRepository: PaymentRepository,
    private val promocodeRepository: PromocodeRepository,
    private val paymentService: PaymentService,
    private val notificationSenderService: NotificationSenderService
) {

    @PostMapping
    fun createBooking(
        principal: Principal,
        @RequestBody dto: BookingRequestDTO
    ): ResponseEntity<BookingResponseDTO> {

        val email = principal.name
        val user = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val seatNumbers = dto.seatNumber.trim()
        val seatCount = seatNumbers.split(",").map { it.trim() }.size

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val booking = Booking(
            userId = userId,
            flightId = dto.flightId,
            seatCount = seatCount,
            seatNumbers = seatNumbers,
            status = BookingStatus.PENDING_PAYMENT,
            bookingDate = LocalDateTime.now()
        )

        val saved = bookingRepository.save(booking)

        notificationSenderService.sendBookingCreated(userId, saved.id)

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BookingResponseDTO(bookingId = saved.id))
    }

    @GetMapping("/my")
    fun getMyBookings(principal: Principal): ResponseEntity<List<BookingDetailsDTO>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val bookings = bookingRepository.findByUserId(userId)

        val result = bookings.map { booking ->
            val flightOptional = flightRepository.findById(booking.flightId)
            val flight = if (flightOptional.isPresent) flightOptional.get() else null

            val payments = paymentRepository.findByBookingId(booking.id)
            val successfulPayment = payments
                .filter { it.status == PaymentStatus.SUCCEEDED }
                .maxByOrNull { it.createdAt }

            val paidAmount = successfulPayment?.let {
                BigDecimal(it.amount).divide(BigDecimal(100))
            }

            val promocode = booking.promocodeId?.let { promocodeRepository.findById(it).orElse(null) }

            BookingDetailsDTO(
                bookingId = booking.id,
                seatNumbers = booking.seatNumbers,
                seatCount = booking.seatCount,
                status = booking.status,
                bookingDate = booking.bookingDate,
                flightId = booking.flightId,
                airlineName = flight?.airlineName ?: "",
                airlineLogo = flight?.airlineLogo ?: "",
                departureCity = flight?.departureCity ?: "",
                arrivalCity = flight?.arrivalCity ?: "",
                departureTime = flight?.departureTime ?: "",
                arriveTime = flight?.arriveTime ?: "",
                flightDate = flight?.flightDate ?: LocalDate.now(),
                classSeat = flight?.classSeat ?: "",
                price = flight?.price ?: BigDecimal.ZERO,
                paidAmount = paidAmount,
                promocodeId = booking.promocodeId,
                promocodeCode = promocode?.code,
                promocodeDiscountPercent = promocode?.discountPercent,
                promocodeDiscountAmount = promocode?.discountAmount
            )
        }

        return ResponseEntity.ok(result)
    }

    @PostMapping("/{id}/cancel")
    fun cancelBooking(
        principal: Principal,
        @PathVariable id: Long
    ): ResponseEntity<Void> {

        val email = principal.name
        val user = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val bookingOptional = bookingRepository.findById(id)
        if (!bookingOptional.isPresent) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        val booking = bookingOptional.get()

        if (booking.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            if (booking.status == BookingStatus.CONFIRMED) {
                val refundSuccess = paymentService.refundBooking(booking.id, email)
                if (!refundSuccess) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            }

            booking.status = BookingStatus.CANCELED
            booking.canceledAt = LocalDateTime.now()
            bookingRepository.save(booking)

            notificationSenderService.sendBookingCancelled(userId, booking.id)

            ResponseEntity.noContent().build()

        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteBooking(
        principal: Principal,
        @PathVariable id: Long
    ): ResponseEntity<Void> {
        val email = principal.name
        val user = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val bookingOptional = bookingRepository.findById(id)
        if (!bookingOptional.isPresent) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        val booking = bookingOptional.get()

        if (booking.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        try {
            paymentRepository.deleteByBookingId(id)
        } catch (e: Exception) {
            // Log but continue
        }

        bookingRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
