package com.wheezy.server.Component

import com.wheezy.server.Models.PointsTransaction
import com.wheezy.server.Models.TierBenefit
import com.wheezy.server.Repository.PointsTransactionRepository
import com.wheezy.server.Repository.TierBenefitRepository
import com.wheezy.server.Repository.UserPointsRepository
import com.wheezy.server.Service.LoyaltyBonusService
import com.wheezy.server.Service.NotificationSenderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class LoyaltyScheduler(
    private val userPointsRepository: UserPointsRepository,
    private val tierBenefitRepository: TierBenefitRepository,
    private val pointsTransactionRepository: PointsTransactionRepository,
    private val loyaltyBonusService: LoyaltyBonusService,
    private val notificationSenderService: NotificationSenderService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    fun recalculateAllTiersAndAwardBonus() {
        val awarded = loyaltyBonusService.awardMonthlyActivityBonus()

        val allTiers = tierBenefitRepository.findAllByOrderByMinPointsAsc()
        val users = userPointsRepository.findAll()

        var demotedCount = 0
        var promotedCount = 0
        val tierChanges = mutableListOf<Triple<Long, String, String>>()

        for (userPoints in users) {
            val newTier = determineTier(userPoints.lifetimePoints, allTiers)
            if (newTier != userPoints.tier) {
                tierChanges.add(Triple(userPoints.userId, userPoints.tier, newTier))
                userPoints.tier = newTier
                userPoints.updatedAt = LocalDateTime.now()
                userPointsRepository.save(userPoints)

                if (getTierLevel(newTier) < getTierLevel(userPoints.tier)) demotedCount++
                else promotedCount++
            }
        }

        for ((userId, oldTier, newTier) in tierChanges) {
            try {
                notificationSenderService.sendTierChangeNotification(userId, oldTier, newTier)
            } catch (e: Exception) {
                log.error("Failed to send tier change notification to user $userId", e)
            }
        }

        expireOldPoints()
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    fun expireOldPoints() {
        val oneYearAgo = LocalDateTime.now().minusMonths(12)
        val expiredTransactions = pointsTransactionRepository.findEarnedPointsOlderThan(oneYearAgo)

        if (expiredTransactions.isEmpty()) {
            return
        }

        val pointsToExpire = expiredTransactions.groupBy { it.userId }
            .mapValues { (_, transactions) -> transactions.sumOf { it.amount.coerceAtLeast(0) } }

        var totalExpired = 0

        for ((userId, pointsToDeduct) in pointsToExpire) {
            if (pointsToDeduct <= 0) continue

            val updated = userPointsRepository.deductPoints(userId, pointsToDeduct)
            if (updated > 0) {
                val transaction = PointsTransaction(
                    userId = userId,
                    amount = -pointsToDeduct,
                    type = "EXPIRATION",
                    description = "$pointsToDeduct points expired (12 months old)"
                )
                pointsTransactionRepository.save(transaction)
                totalExpired += pointsToDeduct

                try {
                    notificationSenderService.sendPointsExpiredNotification(userId, pointsToDeduct)
                } catch (e: Exception) {
                    log.error("Failed to send points expiration notification to user $userId", e)
                }
            }
        }
    }

    private fun determineTier(points: Int, tiers: List<TierBenefit>): String {
        return tiers.lastOrNull { points >= it.minPoints }?.tier ?: "BRONZE"
    }

    private fun getTierLevel(tier: String): Int {
        return when (tier) {
            "BRONZE" -> 0
            "SILVER" -> 1
            "GOLD" -> 2
            "PLATINUM" -> 3
            else -> 0
        }
    }
}