package com.wheezy.server.Service

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Models.PointsTransaction
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.PointsTransactionRepository
import com.wheezy.server.Repository.UserPointsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Service
class LoyaltyBonusService(
    private val bookingRepository: BookingRepository,
    private val userPointsRepository: UserPointsRepository,
    private val pointsTransactionRepository: PointsTransactionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun awardMonthlyActivityBonus(): Int {
        val lastMonth = YearMonth.now().minusMonths(1)
        val startOfMonth = lastMonth.atDay(1).atStartOfDay()
        val endOfMonth = lastMonth.atEndOfMonth().atTime(23, 59, 59)

        val activeUsers = bookingRepository.findAll().filter { booking ->
            booking.status in listOf(BookingStatus.CONFIRMED, BookingStatus.PAID) &&
                    booking.bookingDate in startOfMonth..endOfMonth
        }.groupBy { it.userId }
            .filter { it.value.size >= 3 }
            .keys

        var awardedCount = 0
        for (userId in activeUsers) {
            val alreadyAwarded = pointsTransactionRepository
                .findByUserIdAndType(userId, "MONTHLY_BONUS")
                .any { it.createdAt.isAfter(startOfMonth) }

            if (alreadyAwarded) continue

            try {
                userPointsRepository.addPoints(userId, 200)
                val transaction = PointsTransaction(
                    userId = userId,
                    amount = 200,
                    type = "MONTHLY_BONUS",
                    description = "Monthly activity bonus: 3+ flights in ${lastMonth.month}"
                )
                pointsTransactionRepository.save(transaction)
                awardedCount++
                log.info("Awarded monthly bonus to user $userId")
            } catch (e: Exception) {
                log.error("Failed to award bonus to user $userId", e)
            }
        }
        return awardedCount
    }
}
