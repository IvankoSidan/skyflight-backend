package com.wheezy.server.Component

import com.wheezy.server.Repository.PendingPointsHoldRepository
import com.wheezy.server.Repository.UserPointsRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PointsHoldCleanupScheduler(
    private val pendingPointsHoldRepository: PendingPointsHoldRepository,
    private val userPointsRepository: UserPointsRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    fun expireAndCleanupOldHolds() {
        val now = LocalDateTime.now()

        pendingPointsHoldRepository.expireOldHolds(now)

        val weekAgo = now.minusDays(7)
        pendingPointsHoldRepository.deleteOldHolds(weekAgo)
    }
}