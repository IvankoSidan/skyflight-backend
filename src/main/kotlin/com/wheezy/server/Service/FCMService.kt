package com.wheezy.server.Service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.Notification
import com.wheezy.server.Repository.UserFCMTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class FCMService(
    private val userFCMTokenRepository: UserFCMTokenRepository
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
            logger.debug("FCM disabled")
            return false
        }

        val tokens = userFCMTokenRepository.findByUserId(userId).map { it.token }
        if (tokens.isEmpty()) {
            logger.debug("No FCM tokens for user $userId")
            return false
        }

        return sendMulticast(tokens, title, body, data)
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
                    logger.warn("Removing invalid token: $badToken")
                    userFCMTokenRepository.deleteByToken(badToken)
                }
            }

            logger.info("FCM sent to ${response.successCount}/${tokens.size} devices")
            response.successCount > 0
        } catch (e: Exception) {
            logger.error("FCM sendEach failed", e)
            false
        }
    }
}
