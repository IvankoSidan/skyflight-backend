package com.wheezy.server.Service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.Notification
import com.wheezy.server.Models.FailedNotification
import com.wheezy.server.Repository.FailedNotificationRepository
import com.wheezy.server.Repository.UserFCMTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FCMService(
    private val userFCMTokenRepository: UserFCMTokenRepository,
    private val failedNotificationRepository: FailedNotificationRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${fcm.enabled:true}")
    private var fcmEnabled: Boolean = true

    fun sendNotificationToUser(
        userId: Long,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        logger.info("🔥🔥🔥 FCM sendNotificationToUser: userId=$userId, title=$title, body=$body")

        if (!fcmEnabled) {
            logger.warn("🔥🔥🔥 FCM DISABLED by config")
            return false
        }

        val tokens = userFCMTokenRepository.findByUserId(userId).map { it.token }
        if (tokens.isEmpty()) {
            logger.warn("🔥🔥🔥 FCM NO TOKENS for user $userId")
            return false
        }

        logger.info("🔥🔥🔥 FCM found ${tokens.size} tokens for user $userId")
        tokens.forEachIndexed { index, token ->
            logger.info("🔥🔥🔥 FCM token $index: ${token.take(20)}...")
        }

        val success = sendMulticast(tokens, title, body, data)
        logger.info("🔥🔥🔥 FCM sendMulticast result: $success")

        if (!success) {
            logger.warn("🔥🔥🔥 FCM failed, saving for retry")
            saveForRetry(userId, title, body, data)
        }

        return success
    }

    private fun sendMulticast(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>
    ): Boolean {
        return try {
            logger.info("🔥🔥🔥 FCM sendMulticast: ${tokens.size} tokens")

            val messages = tokens.map { token ->
                com.google.firebase.messaging.Message.builder()
                    .setToken(token)
                    .setNotification(
                        Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build()
                    )
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(
                                AndroidNotification.builder()
                                    .setSound("default")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build()
                            )
                            .build()
                    )
                    .putAllData(data)
                    .build()
            }

            val response = FirebaseMessaging.getInstance().sendEach(messages)
            logger.info("🔥🔥🔥 FCM response: successCount=${response.successCount}, failureCount=${response.failureCount}")

            response.responses.forEachIndexed { index, sendResponse ->
                if (!sendResponse.isSuccessful) {
                    val badToken = tokens[index]
                    logger.warn("🔥🔥🔥 FCM removing invalid token: $badToken, error: ${sendResponse.exception?.message}")
                    userFCMTokenRepository.deleteByToken(badToken)
                }
            }

            response.successCount > 0
        } catch (e: Exception) {
            logger.error("🔥🔥🔥 FCM sendEach failed: ${e.message}", e)
            false
        }
    }

    private fun saveForRetry(userId: Long, title: String, body: String, data: Map<String, String>) {
        try {
            logger.info("🔥🔥🔥 FCM saving for retry: userId=$userId")
            val dataJson = if (data.isNotEmpty()) objectMapper.writeValueAsString(data) else null

            val failedNotification = FailedNotification(
                userId = userId,
                title = title,
                body = body,
                data = dataJson,
                nextRetryAt = calculateNextRetryTime(0)
            )
            failedNotificationRepository.save(failedNotification)
            logger.info("🔥🔥🔥 FCM saved for retry")
        } catch (e: Exception) {
            logger.error("🔥🔥🔥 FCM failed to save notification for retry", e)
        }
    }

    fun processFailedNotifications(): Int {
        val now = LocalDateTime.now()
        val pageable = PageRequest.of(0, 100)
        val pending = failedNotificationRepository.findPendingRetries(now, pageable)

        if (pending.isEmpty()) {
            return 0
        }

        logger.info("🔥🔥🔥 FCM processing ${pending.size} failed notifications")

        var processed = 0

        for (notification in pending) {
            try {
                val tokens = userFCMTokenRepository.findByUserId(notification.userId).map { it.token }

                if (tokens.isEmpty()) {
                    logger.warn("🔥🔥🔥 FCM no tokens for retry ${notification.id}")
                    failedNotificationRepository.markAsFailed(
                        notification.id,
                        "No FCM tokens for user",
                        LocalDateTime.now()
                    )
                    continue
                }

                val dataMap = if (notification.data != null) {
                    objectMapper.readValue(notification.data, Map::class.java) as Map<String, String>
                } else emptyMap()

                val success = sendMulticast(tokens, notification.title, notification.body, dataMap)

                if (success) {
                    failedNotificationRepository.markAsSuccess(notification.id, LocalDateTime.now())
                    processed++
                    logger.info("🔥🔥🔥 FCM retry success: ${notification.id}")
                } else {
                    val newRetryCount = notification.retryCount + 1

                    if (newRetryCount >= notification.maxRetries) {
                        failedNotificationRepository.markAsFailed(
                            notification.id,
                            "Max retries exceeded",
                            LocalDateTime.now()
                        )
                        logger.warn("🔥🔥🔥 FCM retry max exceeded: ${notification.id}")
                    } else {
                        notification.retryCount = newRetryCount
                        notification.nextRetryAt = calculateNextRetryTime(newRetryCount)
                        notification.lastError = "Retry $newRetryCount/${notification.maxRetries} failed"
                        failedNotificationRepository.save(notification)
                        logger.info("🔥🔥🔥 FCM retry scheduled: ${notification.id}, attempt $newRetryCount")
                    }
                }
            } catch (e: Exception) {
                logger.error("🔥🔥🔥 FCM failed to process notification ${notification.id}", e)
                val newRetryCount = notification.retryCount + 1

                if (newRetryCount >= notification.maxRetries) {
                    failedNotificationRepository.markAsFailed(
                        notification.id,
                        e.message?.take(500) ?: "Unknown error",
                        LocalDateTime.now()
                    )
                } else {
                    notification.retryCount = newRetryCount
                    notification.nextRetryAt = calculateNextRetryTime(newRetryCount)
                    notification.lastError = e.message?.take(500)
                    failedNotificationRepository.save(notification)
                }
            }
        }

        logger.info("🔥🔥🔥 FCM processed $processed notifications")
        return processed
    }

    private fun calculateNextRetryTime(retryCount: Int): LocalDateTime {
        val delayMinutes = when (retryCount) {
            0 -> 1
            1 -> 5
            2 -> 15
            3 -> 30
            4 -> 60
            else -> 120
        }
        return LocalDateTime.now().plusMinutes(delayMinutes.toLong())
    }
}