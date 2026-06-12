package com.wheezy.server.Component

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Repository.BookingRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class BookingCleanupScheduler(private val bookingRepository: BookingRepository) {

    // Каждый день в 3 утра
    @Scheduled(cron = "0 0 3 * * ?")
    fun deleteOldCanceledBookings() {
        val cutoff = LocalDateTime.now().minusDays(30)
        bookingRepository.deleteByStatusAndCanceledAtBefore(BookingStatus.CANCELED, cutoff)
    }
}
