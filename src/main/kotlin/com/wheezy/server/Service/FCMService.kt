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
        if (!fcmEnabled) {
            return false
        }

        val tokens = userFCMTokenRepository.findByUserId(userId).map { it.token }
        if (tokens.isEmpty()) {
            logger.warn("No FCM tokens for user $userId")
            return false
        }

        val success = sendMulticast(tokens, title, body, data)

        if (!success) {
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

            response.responses.forEachIndexed { index, sendResponse ->
                if (!sendResponse.isSuccessful) {
                    val badToken = tokens[index]
                    logger.warn("Removing invalid token: $badToken, error: ${sendResponse.exception?.message}")
                    userFCMTokenRepository.deleteByToken(badToken)
                }
            }

            response.successCount > 0
        } catch (e: Exception) {
            logger.error("FCM sendEach failed: ${e.message}", e)
            false
        }
    }

    private fun saveForRetry(userId: Long, title: String, body: String, data: Map<String, String>) {
        try {
            val dataJson = if (data.isNotEmpty()) objectMapper.writeValueAsString(data) else null

            val failedNotification = FailedNotification(
                userId = userId,
                title = title,
                body = body,
                data = dataJson,
                nextRetryAt = calculateNextRetryTime(0)
            )
            failedNotificationRepository.save(failedNotification)
        } catch (e: Exception) {
            logger.error("Failed to save notification for retry", e)
        }
    }

    fun processFailedNotifications(): Int {
        val now = LocalDateTime.now()
        val pageable = PageRequest.of(0, 100)
        val pending = failedNotificationRepository.findPendingRetries(now, pageable)

        if (pending.isEmpty()) {
            return 0
        }

        var processed = 0

        for (notification in pending) {
            try {
                val tokens = userFCMTokenRepository.findByUserId(notification.userId).map { it.token }

                if (tokens.isEmpty()) {
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
                } else {
                    val newRetryCount = notification.retryCount + 1

                    if (newRetryCount >= notification.maxRetries) {
                        failedNotificationRepository.markAsFailed(
                            notification.id,
                            "Max retries exceeded",
                            LocalDateTime.now()
                        )
                    } else {
                        notification.retryCount = newRetryCount
                        notification.nextRetryAt = calculateNextRetryTime(newRetryCount)
                        notification.lastError = "Retry $newRetryCount/${notification.maxRetries} failed"
                        failedNotificationRepository.save(notification)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to process notification ${notification.id}", e)
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