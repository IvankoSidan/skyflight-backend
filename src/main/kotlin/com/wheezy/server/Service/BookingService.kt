package com.wheezy.server.Service

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Models.Booking
import com.wheezy.server.Models.Flight
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.PromocodeRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookingService(
    private val flightRepository: FlightRepository,
    private val bookingRepository: BookingRepository,
    private val promocodeRepository: PromocodeRepository,
    private val notificationSenderService: NotificationSenderService,
    private val agencyId: Long = 0L
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createBookingWithSeatLock(
        userId: Long,
        flightId: Long,
        seatNumbers: String,
        promocodeId: Long? = null,
        maxRetries: Int = 3
    ): Result<Booking> {
        var retries = 0
        var lastError: Exception? = null

        while (retries < maxRetries) {
            try {
                val flight = flightRepository.findByIdWithPessimisticLock(flightId)
                    ?: return Result.failure(IllegalArgumentException("Flight not found: $flightId"))

                val requestedSeats = seatNumbers.split(",").map { it.trim() }
                val currentReserved = flight.reservedSeats.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                val alreadyTaken = requestedSeats.filter { currentReserved.contains(it) }
                if (alreadyTaken.isNotEmpty()) {
                    return Result.failure(
                        IllegalStateException("Seats already taken: ${alreadyTaken.joinToString()}")
                    )
                }

                val newReservedSeats = (currentReserved + requestedSeats).joinToString(",")
                flight.reservedSeats = newReservedSeats
                flightRepository.save(flight)

                if (promocodeId != null) {
                    promocodeRepository.incrementUsageCount(promocodeId)
                }

                val booking = Booking(
                    userId = userId,
                    agencyId = agencyId,
                    flightId = flightId,
                    seatCount = requestedSeats.size,
                    seatNumbers = seatNumbers,
                    status = BookingStatus.PENDING_PAYMENT,
                    promocodeId = promocodeId
                )
                val savedBooking = bookingRepository.save(booking)

                try {
                    notificationSenderService.sendBookingCreated(userId, savedBooking.id)
                } catch (e: Exception) {
                    log.error("Failed to send notification for booking ${savedBooking.id}", e)
                }

                return Result.success(savedBooking)

            } catch (e: ObjectOptimisticLockingFailureException) {
                retries++
                log.warn("Optimistic lock conflict for flight $flightId, retry $retries/$maxRetries")
                lastError = e
                if (retries >= maxRetries) {
                    return Result.failure(IllegalStateException("Seat booking conflict. Please try again.", e))
                }
                Thread.sleep(100)
            } catch (e: Exception) {
                log.error("Unexpected error during booking", e)
                return Result.failure(e)
            }
        }

        return Result.failure(lastError ?: IllegalStateException("Failed to book seats"))
    }

    @Transactional
    fun releaseSeats(booking: Booking) {
        try {
            log.info("Releasing seats for booking ${booking.id}: ${booking.seatNumbers}")

            val flight = flightRepository.findByIdWithPessimisticLock(booking.flightId)
            if (flight == null) {
                log.warn("Flight not found for booking ${booking.id}, flightId=${booking.flightId}")
                return
            }

            val seatsToRelease = booking.seatNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (seatsToRelease.isEmpty()) {
                log.warn("No seats to release for booking ${booking.id}")
                return
            }

            val currentReserved = flight.reservedSeats.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            log.info("Current reserved seats for flight ${flight.flightId}: $currentReserved")
            log.info("Seats to release: $seatsToRelease")

            val updatedReserved = currentReserved.filter { !seatsToRelease.contains(it) }
            flight.reservedSeats = updatedReserved.joinToString(",")
            flightRepository.save(flight)

            log.info("Released seats ${seatsToRelease.joinToString()} for flight ${flight.flightId}")
            log.info("Updated reserved seats: ${flight.reservedSeats}")

        } catch (e: Exception) {
            log.error("Failed to release seats for booking ${booking.id}", e)
            throw e
        }
    }

    @Transactional
    fun releaseSeatsByBookingId(bookingId: Long) {
        try {
            val booking = bookingRepository.findById(bookingId).orElse(null)
            if (booking == null) {
                log.warn("Booking not found: $bookingId")
                return
            }
            releaseSeats(booking)
        } catch (e: Exception) {
            log.error("Failed to release seats for booking $bookingId", e)
            throw e
        }
    }

    fun updateBookingStatus(bookingId: Long, status: BookingStatus): Result<Booking> {
        return try {
            val booking = bookingRepository.findById(bookingId).orElse(null)
                ?: return Result.failure(IllegalArgumentException("Booking not found"))

            booking.status = status
            if (status == BookingStatus.CANCELED) {
                booking.canceledAt = java.time.LocalDateTime.now()
            }
            val saved = bookingRepository.save(booking)
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}