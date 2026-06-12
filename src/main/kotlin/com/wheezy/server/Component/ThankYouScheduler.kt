package com.wheezy.server.Component

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.ReviewRepository
import com.wheezy.server.Service.NotificationSenderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ThankYouScheduler(
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val reviewRepository: ReviewRepository,
    private val notificationSenderService: NotificationSenderService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sentThankYou = mutableSetOf<String>()


    @Scheduled(cron = "0 0 10,18 * * ?")
    fun sendReviewReminders() {
        val yesterday = LocalDate.now().minusDays(1)
        val flights = flightRepository.findByFlightDate(yesterday)

        for (flight in flights) {
            val bookings = bookingRepository.findByFlightId(flight.flightId)
            val confirmedBookings = bookings.filter {
                it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.PAID
            }

            for (booking in confirmedBookings) {
                val hasReview = reviewRepository.existsByBookingId(booking.id)
                if (!hasReview) {
                    try {
                        notificationSenderService.sendReviewReminder(
                            userId = booking.userId,
                            bookingId = booking.id,
                            flight = flight
                        )
                        log.info("Review reminder sent for booking ${booking.id}")
                    } catch (e: Exception) {
                        log.error("Failed to send review reminder for booking ${booking.id}", e)
                    }
                }
            }
        }
    }

    @Scheduled(cron = "0 0 20 * * ?")
    fun sendThankYouAfterFlight() {
        val yesterday = LocalDate.now().minusDays(1)
        val flights = flightRepository.findByFlightDate(yesterday)

        for (flight in flights) {
            val bookings = bookingRepository.findByFlightId(flight.flightId)
            val confirmedBookings = bookings.filter {
                it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.PAID
            }

            for (booking in confirmedBookings) {
                val key = "thank_you_${booking.id}"
                if (!sentThankYou.contains(key)) {
                    try {
                        notificationSenderService.sendThankYouAfterFlight(
                            userId = booking.userId,
                            bookingId = booking.id,
                            flight = flight
                        )
                        sentThankYou.add(key)
                        log.info("Thank you notification sent for booking ${booking.id}")
                    } catch (e: Exception) {
                        log.error("Failed to send thank you for booking ${booking.id}", e)
                    }
                }
            }
        }

        if (sentThankYou.size > 1000) {
            sentThankYou.clear()
        }
    }
}
