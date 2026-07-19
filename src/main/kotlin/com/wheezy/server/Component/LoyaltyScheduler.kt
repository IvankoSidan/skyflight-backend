package com.wheezy.server.Component

import com.wheezy.server.Models.TierBenefit
import com.wheezy.server.Models.PointsTransaction
import com.wheezy.server.Repository.PointsTransactionRepository
import com.wheezy.server.Repository.TierBenefitRepository
import com.wheezy.server.Repository.UserPointsRepository
import com.wheezy.server.Service.LoyaltyBonusService
import com.wheezy.server.Service.NotificationSenderService
import jakarta.annotation.PostConstruct
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

    @PostConstruct
    fun initTiers() {
        if (tierBenefitRepository.count() == 0L) {
            log.info("Initializing loyalty tiers...")
            val tiers = listOf(
                TierBenefit(
                    tier = "BRONZE",
                    cashbackPercent = 5,
                    freeSeatSelection = false,
                    priorityBoarding = false,
                    freeBaggageKg = 0,
                    minPoints = 0
                ),
                TierBenefit(
                    tier = "SILVER",
                    cashbackPercent = 10,
                    freeSeatSelection = true,
                    priorityBoarding = false,
                    freeBaggageKg = 0,
                    minPoints = 1000
                ),
                TierBenefit(
                    tier = "GOLD",
                    cashbackPercent = 15,
                    freeSeatSelection = true,
                    priorityBoarding = true,
                    freeBaggageKg = 0,
                    minPoints = 5000
                ),
                TierBenefit(
                    tier = "PLATINUM",
                    cashbackPercent = 20,
                    freeSeatSelection = true,
                    priorityBoarding = true,
                    freeBaggageKg = 20,
                    minPoints = 20000
                )
            )
            tierBenefitRepository.saveAll(tiers)
            log.info("Tiers initialized: ${tierBenefitRepository.count()}")
        }
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    fun recalculateAllTiersAndAwardBonus() {
        val awarded = loyaltyBonusService.awardMonthlyActivityBonus()
        log.info("Monthly activity bonus awarded to $awarded users")

        val allTiers = tierBenefitRepository.findAllByOrderByMinPointsAsc()
        val users = userPointsRepository.findAll()

        var changedCount = 0
        for (userPoints in users) {
            val newTier = determineTier(userPoints.lifetimePoints, allTiers)
            val currentTier = userPoints.tier ?: "BRONZE"
            if (newTier != currentTier) {
                val oldTier = currentTier
                userPoints.tier = newTier
                userPoints.updatedAt = LocalDateTime.now()
                userPointsRepository.save(userPoints)
                changedCount++

                try {
                    notificationSenderService.sendTierChangeNotification(userPoints.userId, oldTier, newTier)
                } catch (e: Exception) {
                    log.error("Failed to send tier change notification to user ${userPoints.userId}", e)
                }
            }
        }
        log.info("Tier recalculated: $changedCount users changed")

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

            val updated = userPointsRepository.deductPointsWithTierUpdate(userId, pointsToDeduct)
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

        log.info("Expired $totalExpired points from ${pointsToExpire.size} users")
    }

    private fun determineTier(points: Int, tiers: List<TierBenefit>): String {
        return tiers.lastOrNull { points >= it.minPoints }?.tier ?: "BRONZE"
    }
}