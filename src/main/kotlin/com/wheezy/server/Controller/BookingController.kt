package com.wheezy.server.Controller

import com.wheezy.server.DTO.BookingDetailsDTO
import com.wheezy.server.DTO.BookingRequestDTO
import com.wheezy.server.DTO.BookingResponseDTO
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.PaymentRepository
import com.wheezy.server.Repository.PointsTransactionRepository
import com.wheezy.server.Repository.PromocodeRepository
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.BookingService
import com.wheezy.server.Service.NotificationSenderService
import com.wheezy.server.Service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
    private val pointsTransactionRepository: PointsTransactionRepository,
    private val paymentService: PaymentService,
    private val notificationSenderService: NotificationSenderService,
    private val bookingService: BookingService
) {

    @PostMapping
    fun createBooking(
        principal: Principal,
        @RequestBody dto: BookingRequestDTO
    ): ResponseEntity<BookingResponseDTO> {
        val email = principal.name
        val user = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        val agencyId = user.agencyId ?: 1L

        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        if (dto.promocodeId != null) {
            val promocode = promocodeRepository.findById(dto.promocodeId).orElse(null)
            if (promocode == null || !promocode.isActive) {
                return ResponseEntity.badRequest().build()
            }
            if (promocode.validFrom.isAfter(LocalDateTime.now()) || promocode.validUntil.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().build()
            }
            val maxUses = promocode.maxUses
            if (maxUses != null && promocode.usedCount >= maxUses) {
                return ResponseEntity.badRequest().build()
            }
        }

        val seatNumbers = dto.seatNumber.trim()
        val seatList = seatNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (seatList.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val result = bookingService.createBookingWithSeatLock(
            userId = userId,
            flightId = dto.flightId,
            seatNumbers = seatNumbers,
            promocodeId = dto.promocodeId,
            agencyId = agencyId
        )

        return if (result.isSuccess) {
            val booking = result.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED)
                .body(BookingResponseDTO(bookingId = booking.id))
        } else {
            val error = result.exceptionOrNull()
            when (error) {
                is IllegalStateException -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(BookingResponseDTO(bookingId = null))
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponseDTO(bookingId = null))
            }
        }
    }

    @GetMapping("/flight/{flightId}")
    @PreAuthorize("permitAll()")
    fun getBookedSeats(@PathVariable flightId: Long): ResponseEntity<List<String>> {
        return try {
            val flight = flightRepository.findById(flightId)
            if (flight.isEmpty) {
                return ResponseEntity.notFound().build()
            }
            val reservedSeats = flight.get().reservedSeats
            val seats = if (reservedSeats.isNotEmpty()) {
                reservedSeats.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            ResponseEntity.ok(seats)
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
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

            val refundPayment = payments
                .filter { it.status == PaymentStatus.REFUNDED || it.refundId != null }
                .maxByOrNull { it.createdAt }

            val paidAmount = successfulPayment?.let {
                BigDecimal(it.amount).divide(BigDecimal(100))
            }

            val promocode = booking.promocodeId?.let { promocodeRepository.findById(it).orElse(null) }
            val passenger = userRepository.findById(booking.userId).orElse(null)

            val flightPrice = flight?.price ?: BigDecimal.ZERO
            val totalPrice = flightPrice.multiply(BigDecimal(booking.seatCount))

            var discountAmount = BigDecimal.ZERO

            promocode?.let { promo ->
                val discountAmountValue = promo.discountAmount
                val discountPercentValue = promo.discountPercent
                discountAmount = when {
                    discountAmountValue != null -> BigDecimal(discountAmountValue).divide(BigDecimal(100))
                    discountPercentValue != null -> totalPrice.multiply(BigDecimal(discountPercentValue)).divide(BigDecimal(100))
                    else -> BigDecimal.ZERO
                }
            }

            val loyaltyTransactions = pointsTransactionRepository.findByUserIdAndType(userId, "REDEMPTION")
                .filter { it.referenceId == booking.id }
            val loyaltyPointsUsed = loyaltyTransactions.sumOf { -it.amount }
            val loyaltyDiscountAmount = BigDecimal(loyaltyPointsUsed / 100)

            val totalDiscount = discountAmount + loyaltyDiscountAmount
            val finalPrice = totalPrice - totalDiscount

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
                promocodeDiscountAmount = promocode?.discountAmount,
                passengerName = passenger?.name,
                passengerEmail = passenger?.email,
                paymentMethod = successfulPayment?.let {
                    "Card •••• ${it.providerPaymentId.takeLast(4)}"
                } ?: "N/A",
                paymentDate = successfulPayment?.createdAt,
                paymentStatus = successfulPayment?.status?.name ?: "N/A",
                paymentAmount = successfulPayment?.let {
                    BigDecimal(it.amount).divide(BigDecimal(100))
                },
                refundStatus = refundPayment?.status?.name,
                refundDate = refundPayment?.updatedAt,
                refundAmount = refundPayment?.let {
                    BigDecimal(it.amount).divide(BigDecimal(100))
                },
                invoiceUrl = "/api/invoices/booking/${booking.id}",
                ticketUrl = "/ticket/${booking.id}",
                totalPrice = totalPrice,
                discountAmount = totalDiscount,
                finalPrice = finalPrice
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
            if (booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.PAID) {
                val refundSuccess = paymentService.refundBooking(booking.id, email)
                if (!refundSuccess) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            }

            bookingService.releaseSeats(booking)

            booking.status = BookingStatus.CANCELED
            booking.canceledAt = LocalDateTime.now()
            bookingRepository.save(booking)

            notificationSenderService.sendBookingCancelled(userId, booking.id)

            ResponseEntity.noContent().build()
        } catch (_: Exception) {
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
            bookingService.releaseSeats(booking)

            paymentRepository.deleteByBookingId(id)
            bookingRepository.deleteById(id)
            return ResponseEntity.noContent().build()
        } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}
