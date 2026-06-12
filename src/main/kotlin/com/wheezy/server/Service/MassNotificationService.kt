package com.wheezy.server.Service

import com.wheezy.server.DTO.MassNotificationRequest
import com.wheezy.server.DTO.MassNotificationResponse
import com.wheezy.server.Repository.UserFCMTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class MassNotificationService(
    private val fcmService: FCMService,
    private val userFCMTokenRepository: UserFCMTokenRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newFixedThreadPool(10)

    fun sendMassNotification(request: MassNotificationRequest): MassNotificationResponse {
        return try {
            val users = userFCMTokenRepository.findAll().map { it.userId }.distinct()

            if (users.isEmpty()) {
                return MassNotificationResponse(
                    success = false,
                    message = "No users with FCM tokens found"
                )
            }

            var successCount = 0
            var failCount = 0

            val jobs = users.map { userId ->
                executor.submit {
                    try {
                        val sent = fcmService.sendNotificationToUser(
                            userId = userId,
                            title = request.title,
                            body = request.message,
                            data = mapOf(
                                "type" to "mass_promotion",
                                "imageUrl" to (request.imageUrl ?: ""),
                                "actionUrl" to (request.actionUrl ?: "")
                            )
                        )
                        if (sent) successCount else failCount
                    } catch (e: Exception) {
                        log.error("Failed to send to user $userId", e)
                        failCount
                    }
                }
            }

            jobs.forEach { it.get(30, TimeUnit.SECONDS) }

            log.info("Mass notification sent to $successCount users, failed: $failCount")

            MassNotificationResponse(
                success = true,
                message = "Notification sent",
                recipientsCount = successCount,
                failedCount = failCount
            )

        } catch (e: Exception) {
            log.error("Mass notification failed", e)
            MassNotificationResponse(
                success = false,
                message = "Failed: ${e.message}"
            )
        }
    }
}
