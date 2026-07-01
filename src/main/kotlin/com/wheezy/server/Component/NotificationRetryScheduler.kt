package com.wheezy.server.Component

import com.wheezy.server.Repository.FailedNotificationRepository
import com.wheezy.server.Service.FCMService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class NotificationRetryScheduler(
    private val fcmService: FCMService,
    private val failedNotificationRepository: FailedNotificationRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 120000, initialDelay = 30000)
    fun processFailedNotifications() {
        try {
            fcmService.processFailedNotifications()
        } catch (e: Exception) {
            log.error("Failed to process failed notifications", e)
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    fun cleanupOldNotifications() {
        try {
            val weekAgo = LocalDateTime.now().minusDays(7)
            failedNotificationRepository.deleteOldNotifications(weekAgo)
        } catch (e: Exception) {
            log.error("Failed to cleanup old notifications", e)
        }
    }
}