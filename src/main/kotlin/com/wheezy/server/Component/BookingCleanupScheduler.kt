package com.wheezy.server.Component

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Service.BookingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class BookingCleanupScheduler(
    private val bookingRepository: BookingRepository,
    private val bookingService: BookingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1800000, initialDelay = 60000)
    @Transactional
    fun cleanupExpiredPendingBookings() {
        val cutoff = LocalDateTime.now().minusMinutes(30)
        val expiredBookings = bookingRepository.findByStatusAndBookingDateBefore(
            BookingStatus.PENDING_PAYMENT, cutoff
        )

        if (expiredBookings.isEmpty()) {
            return
        }

        log.info("🔄 Found ${expiredBookings.size} expired pending bookings")

        var releasedCount = 0
        for (booking in expiredBookings) {
            try {
                // ✅ ОСВОБОЖДАЕМ МЕСТА
                bookingService.releaseSeats(booking)

                booking.status = BookingStatus.FAILED
                booking.canceledAt = LocalDateTime.now()
                bookingRepository.save(booking)
                releasedCount++
                log.info("✅ Released seats for expired booking ${booking.id}")
            } catch (e: Exception) {
                log.error("❌ Failed to release seats for booking ${booking.id}", e)
            }
        }

        log.info("✅ Released seats for $releasedCount expired bookings")
    }

    // Каждый день в 3 утра удаляем старые CANCELED бронирования
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    fun deleteOldCanceledBookings() {
        val cutoff = LocalDateTime.now().minusDays(30)
        val deleted = bookingRepository.deleteByStatusAndCanceledAtBefore(BookingStatus.CANCELED, cutoff)
        log.info("✅ Deleted $deleted old canceled bookings")
    }
}