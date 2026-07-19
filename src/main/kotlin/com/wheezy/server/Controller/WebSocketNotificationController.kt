package com.wheezy.server.Controller

import com.wheezy.server.Models.Notification
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller

@Controller
class WebSocketNotificationController(
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @MessageMapping("/notifications/subscribe")
    fun subscribeToNotifications() {
        try {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated) {
                logger.warn("WebSocket subscription attempt without authentication")
                return
            }

            val userEmail = authentication.name
            val user = userRepository.findByEmail(userEmail)
            if (user == null) {
                logger.warn("User not found: $userEmail")
                return
            }

            messagingTemplate.convertAndSendToUser(
                userEmail,
                "/queue/notifications/subscribed",
                mapOf(
                    "type" to "subscription",
                    "status" to "success",
                    "message" to "Subscribed to notifications",
                    "userId" to user.id
                )
            )
            logger.info("User $userEmail subscribed to notifications")
        } catch (e: Exception) {
            logger.error("Error subscribing to notifications: ${e.message}", e)
        }
    }

    @MessageMapping("/bookings/subscribe")
    fun subscribeToBookingUpdates() {
        try {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated) {
                logger.warn("WebSocket booking subscription attempt without authentication")
                return
            }

            val userEmail = authentication.name
            val user = userRepository.findByEmail(userEmail)
            if (user == null) {
                logger.warn("User not found: $userEmail")
                return
            }

            messagingTemplate.convertAndSendToUser(
                userEmail,
                "/queue/bookings/subscribed",
                mapOf(
                    "type" to "subscription",
                    "status" to "success",
                    "message" to "Subscribed to booking updates",
                    "userId" to user.id
                )
            )
            logger.info("User $userEmail subscribed to booking updates")
        } catch (e: Exception) {
            logger.error("Error subscribing to booking updates: ${e.message}", e)
        }
    }

    fun sendNotificationToUser(userEmail: String, notification: Notification) {
        try {
            val user = userRepository.findByEmail(userEmail)
            if (user == null) {
                logger.warn("Cannot send notification to non-existent user: $userEmail")
                return
            }

            messagingTemplate.convertAndSendToUser(
                userEmail,
                "/queue/notifications",
                mapOf(
                    "type" to "notification",
                    "data" to mapOf(
                        "id" to notification.id,
                        "message" to notification.message,
                        "isRead" to notification.isRead,
                        "timestamp" to notification.timestamp.toString()
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to send WebSocket notification to $userEmail", e)
        }
    }

    fun sendBookingUpdate(userEmail: String, bookingId: Long, status: String, seatNumbers: String? = null) {
        try {
            val data = mutableMapOf(
                "bookingId" to bookingId,
                "status" to status
            )
            seatNumbers?.let { data["seatNumbers"] = it }

            messagingTemplate.convertAndSendToUser(
                userEmail,
                "/queue/booking-updates",
                mapOf(
                    "type" to "booking_update",
                    "data" to data
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to send WebSocket booking update to $userEmail", e)
        }
    }

    fun sendPaymentUpdate(userEmail: String, bookingId: Long, paymentStatus: String, amount: Long? = null) {
        try {
            val data = mutableMapOf(
                "bookingId" to bookingId,
                "paymentStatus" to paymentStatus
            )
            amount?.let { data["amount"] = it }

            messagingTemplate.convertAndSendToUser(
                userEmail,
                "/queue/payment-updates",
                mapOf(
                    "type" to "payment_update",
                    "data" to data
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to send WebSocket payment update to $userEmail", e)
        }
    }
}