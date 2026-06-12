package com.wheezy.server.Controller

import com.wheezy.server.Models.Notification
import com.wheezy.server.Repository.UserRepository
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class WebSocketNotificationController(
    private val messagingTemplate: SimpMessagingTemplate,
    private val userRepository: UserRepository
) {

    @MessageMapping("/notifications/subscribe")
    fun subscribeToNotifications(principal: Principal) {
        val userEmail = principal.name
        messagingTemplate.convertAndSendToUser(
            userEmail,
            "/queue/notifications/subscribed",
            mapOf(
                "type" to "subscription",
                "status" to "success",
                "message" to "Subscribed to notifications"
            )
        )
    }

    @MessageMapping("/bookings/subscribe")
    fun subscribeToBookingUpdates(principal: Principal) {
        val userEmail = principal.name
        messagingTemplate.convertAndSendToUser(
            userEmail,
            "/queue/bookings/subscribed",
            mapOf(
                "type" to "subscription",
                "status" to "success",
                "message" to "Subscribed to booking updates"
            )
        )
    }

    fun sendNotificationToUser(userEmail: String, notification: Notification) {
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
    }

    fun sendBookingUpdate(userEmail: String, bookingId: Long, status: String, seatNumbers: String? = null) {
        messagingTemplate.convertAndSendToUser(
            userEmail,
            "/queue/booking-updates",
            mapOf(
                "type" to "booking_update",
                "data" to mapOf(
                    "bookingId" to bookingId,
                    "status" to status,
                    "seatNumbers" to (seatNumbers ?: "")
                )
            )
        )
    }

    fun sendPaymentUpdate(userEmail: String, bookingId: Long, paymentStatus: String, amount: Long? = null) {
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
    }

    fun sendSeatUpdate(userEmail: String, flightId: Long, seatNumber: String, isBooked: Boolean) {
        messagingTemplate.convertAndSendToUser(
            userEmail,
            "/queue/seat-updates",
            mapOf(
                "type" to "seat_update",
                "data" to mapOf(
                    "flightId" to flightId,
                    "seatNumber" to seatNumber,
                    "isBooked" to isBooked
                )
            )
        )
    }
}
