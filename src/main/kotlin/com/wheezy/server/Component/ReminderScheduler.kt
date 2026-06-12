package com.wheezy.server.Component

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.NotificationSenderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ReminderScheduler(
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val userRepository: UserRepository,
    private val notificationSenderService: NotificationSenderService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sentReminders = mutableSetOf<String>()

    @Scheduled(cron = "0 0 10,14,18 * * ?")
    fun sendFlightReminders() {
        val tomorrow = LocalDate.now().plusDays(1)
        val flights = flightRepository.findByFlightDate(tomorrow)

        log.info("Checking reminders for flights on $tomorrow. Found ${flights.size} flights.")

        for (flight in flights) {
            val bookings = bookingRepository.findByFlightId(flight.flightId)
            val confirmedBookings = bookings.filter { it.status == BookingStatus.CONFIRMED }

            log.info("Flight ${flight.flightId} (${flight.departureCity}→${flight.arrivalCity}) has ${confirmedBookings.size} confirmed bookings.")

            for (booking in confirmedBookings) {
                val reminderKey = "reminder_${booking.id}_${tomorrow}"

                if (!sentReminders.contains(reminderKey)) {
                    try {
                        notificationSenderService.sendReminder(
                            userId = booking.userId,
                            bookingId = booking.id,
                            flight = flight
                        )
                        sentReminders.add(reminderKey)
                        log.info("Reminder sent for booking ${booking.id} (user ${booking.userId})")
                    } catch (e: Exception) {
                        log.error("Failed to send reminder for booking ${booking.id}", e)
                    }
                } else {
                    log.debug("Reminder already sent for booking ${booking.id}")
                }
            }
        }

        if (sentReminders.size > 1000) {
            log.info("Clearing reminder cache (size: ${sentReminders.size})")
            sentReminders.clear()
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    fun cleanReminderCache() {
        val oldSize = sentReminders.size
        sentReminders.clear()
        log.info("Cleared reminder cache. Removed $oldSize entries.")
    }
}
