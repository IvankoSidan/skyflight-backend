package com.wheezy.server.Service

import com.wheezy.server.Controller.WebSocketNotificationController
import com.wheezy.server.DTO.NotificationSettingsDTO
import com.wheezy.server.Models.Booking
import com.wheezy.server.Models.Flight
import com.wheezy.server.Models.Notification
import com.wheezy.server.Models.User
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.UserNotificationSettingsRepository
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
class NotificationSenderService(
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val fcmService: FCMService,
    private val webSocketController: WebSocketNotificationController,
    private val settingsRepository: UserNotificationSettingsRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val isSending = AtomicBoolean(false)

    private val lastSentCache = mutableMapOf<String, Long>()
    private val sentNotificationsCache = mutableMapOf<String, Long>()

    private val DEDUPLICATE_WINDOW_MS = 5000L
    private val REMINDER_COOLDOWN_SECONDS = 86400L

    private fun canSend(notificationType: String, userId: Long, cooldownSeconds: Long = 60): Boolean {
        val key = "${notificationType}_${userId}"
        val last = lastSentCache[key] ?: 0L
        return System.currentTimeMillis() - last > cooldownSeconds * 1000
    }

    private fun isDuplicate(notificationType: String, userId: Long, message: String): Boolean {
        val key = "${notificationType}_${userId}_${message.take(50)}"
        val last = sentNotificationsCache[key] ?: 0L
        return System.currentTimeMillis() - last < DEDUPLICATE_WINDOW_MS
    }

    private fun markSent(notificationType: String, userId: Long, message: String) {
        val key = "${notificationType}_${userId}_${message.take(50)}"
        sentNotificationsCache[key] = System.currentTimeMillis()
        if (sentNotificationsCache.size > 500) {
            sentNotificationsCache.clear()
        }
    }

    private fun shouldSendNotification(userId: Long, notificationType: String): Boolean {
        return try {
            val settingsOpt = settingsRepository.findByUserId(userId)
            if (settingsOpt.isPresent) {
                settingsOpt.get().shouldSendNotification(notificationType)
            } else {
                val defaultSettings = NotificationSettingsDTO().toEntity(userId)
                settingsRepository.save(defaultSettings)
                true
            }
        } catch (e: Exception) {
            log.error("Error checking notification settings", e)
            true
        }
    }

    private fun sendAllChannels(
        userId: Long,
        title: String,
        message: String,
        notificationType: String,
        data: Map<String, String>,
        webSocketAction: (String) -> Unit
    ) {
        log.info("sendAllChannels START: userId=$userId, type=$notificationType, title=$title")

        if (!shouldSendNotification(userId, notificationType)) {
            log.warn("sendAllChannels BLOCKED by settings: userId=$userId, type=$notificationType")
            return
        }

        if (isDuplicate(notificationType, userId, message)) {
            log.warn("sendAllChannels BLOCKED by duplicate: userId=$userId, type=$notificationType")
            return
        }
        markSent(notificationType, userId, message)

        if (!isSending.compareAndSet(false, true)) {
            log.warn("sendAllChannels BLOCKED: another send in progress, userId=$userId")
            return
        }

        log.info("sendAllChannels ACQUIRED lock: userId=$userId")

        try {
            val userOpt = userRepository.findById(userId)
            if (userOpt.isPresent) {
                val user = userOpt.get()
                log.info("sendAllChannels user found: id=${user.id}, email=${user.email}")

                try {
                    log.info("sendAllChannels sending WebSocket to ${user.email}")
                    webSocketAction(user.email)
                    log.info("sendAllChannels WebSocket sent")
                } catch (e: Exception) {
                    log.error("sendAllChannels WebSocket failed for user ${user.id}", e)
                }

                try {
                    log.info("sendAllChannels calling FCM service for userId=$userId")
                    val fcmResult = fcmService.sendNotificationToUser(
                        userId = userId,
                        title = title,
                        body = message,
                        data = data
                    )
                    log.info("sendAllChannels FCM result: $fcmResult")
                } catch (e: Exception) {
                    log.error("sendAllChannels FCM failed for user ${user.id}", e)
                }
            } else {
                log.warn("sendAllChannels user NOT FOUND: userId=$userId")
            }
        } finally {
            isSending.set(false)
            log.info("sendAllChannels RELEASED lock: userId=$userId")
        }

        log.info("sendAllChannels FINISHED: userId=$userId, type=$notificationType")
    }

    fun sendPointsAwarded(userId: Long, points: Int, reason: String) {
        log.info("sendPointsAwarded: userId=$userId, points=$points, reason=$reason")
        sendAllChannels(
            userId = userId,
            title = "Points Awarded!",
            message = "You earned $points points for $reason",
            notificationType = "points_awarded",
            data = mapOf(
                "type" to "points_awarded",
                "points" to points.toString(),
                "reason" to reason
            )
        ) {}
    }

    fun sendPointsDeducted(userId: Long, points: Int, reason: String) {
        log.info("sendPointsDeducted: userId=$userId, points=$points, reason=$reason")
        sendAllChannels(
            userId = userId,
            title = "Points Deducted",
            message = "$points points used for $reason",
            notificationType = "points_deducted",
            data = mapOf(
                "type" to "points_deducted",
                "points" to points.toString(),
                "reason" to reason
            )
        ) {}
    }

    fun sendPointsReturned(userId: Long, points: Int, reason: String) {
        log.info("sendPointsReturned: userId=$userId, points=$points, reason=$reason")
        sendAllChannels(
            userId = userId,
            title = "Points Returned",
            message = "$points points returned for $reason",
            notificationType = "points_returned",
            data = mapOf(
                "type" to "points_returned",
                "points" to points.toString(),
                "reason" to reason
            )
        ) {}
    }

    fun sendTierChangeNotification(userId: Long, oldTier: String, newTier: String) {
        log.info("sendTierChangeNotification: userId=$userId, oldTier=$oldTier, newTier=$newTier")
        val message = when {
            getTierLevel(newTier) > getTierLevel(oldTier) ->
                "Congratulations! You've been upgraded to $newTier tier!"
            else ->
                "Your loyalty tier has been updated to $newTier"
        }

        sendAllChannels(
            userId = userId,
            title = "Loyalty Tier Update",
            message = message,
            notificationType = "tier_change",
            data = mapOf(
                "type" to "tier_change",
                "oldTier" to oldTier,
                "newTier" to newTier
            )
        ) {}
    }

    fun sendPointsExpiredNotification(userId: Long, points: Int) {
        log.info("sendPointsExpiredNotification: userId=$userId, points=$points")
        sendAllChannels(
            userId = userId,
            title = "Points Expiration",
            message = "$points points have expired",
            notificationType = "points_expired",
            data = mapOf(
                "type" to "points_expired",
                "points" to points.toString()
            )
        ) {}
    }

    fun sendNotification(userId: Long, notification: Notification) {
        log.info("sendNotification: userId=$userId, notificationId=${notification.id}")
        sendAllChannels(
            userId,
            "SkyFlight Notification",
            notification.message,
            "notification",
            mapOf(
                "type" to "notification",
                "notificationId" to notification.id.toString()
            )
        ) { email ->
            webSocketController.sendNotificationToUser(email, notification)
        }
    }

    fun sendBookingCreated(userId: Long, bookingId: Long) {
        log.info("sendBookingCreated: userId=$userId, bookingId=$bookingId")
        sendAllChannels(
            userId,
            "Booking Created",
            "Your booking #$bookingId has been created. Complete payment to confirm.",
            "booking_created",
            mapOf(
                "type" to "booking_created",
                "bookingId" to bookingId.toString()
            )
        ) { email ->
            webSocketController.sendBookingUpdate(email, bookingId, "CREATED", null)
        }
    }

    fun sendBookingConfirmed(userId: Long, bookingId: Long, amount: Long) {
        log.info("sendBookingConfirmed: userId=$userId, bookingId=$bookingId, amount=$amount")
        val booking = getBooking(bookingId)
        val flight = booking?.let { getFlight(it.flightId) }

        if (booking == null || flight == null) {
            log.error("sendBookingConfirmed: booking or flight not found for bookingId $bookingId")
            return
        }

        sendAllChannels(
            userId,
            "Booking Confirmed",
            "Your flight ${flight.departureCity} → ${flight.arrivalCity} is confirmed",
            "booking_confirmed",
            data = mapOf(
                "type" to "booking_confirmed",
                "bookingId" to bookingId.toString(),
                "flightId" to flight.flightId.toString()
            )
        ) { email ->
            webSocketController.sendBookingUpdate(email, bookingId, "CONFIRMED", booking.seatNumbers)
        }
    }

    fun sendPaymentSuccess(userId: Long, bookingId: Long, amount: Long) {
        log.info("sendPaymentSuccess: userId=$userId, bookingId=$bookingId, amount=$amount")
        val amountStr = String.format("%.2f", amount / 100.0)
        val booking = getBooking(bookingId)
        val flight = booking?.let { getFlight(it.flightId) }

        sendAllChannels(
            userId,
            "Payment Successful",
            "Payment $$amountStr for booking #$bookingId successful",
            "payment_success",
            data = mapOf(
                "type" to "payment_success",
                "bookingId" to bookingId.toString(),
                "amount" to amountStr
            )
        ) { email ->
            webSocketController.sendPaymentUpdate(email, bookingId, "SUCCESS", amount)
        }
    }

    fun sendPaymentFailed(userId: Long, bookingId: Long, errorMessage: String? = null) {
        log.info("sendPaymentFailed: userId=$userId, bookingId=$bookingId, error=$errorMessage")
        sendAllChannels(
            userId,
            "Payment Failed",
            "Payment for booking #$bookingId failed${errorMessage?.let { ": $it" } ?: ""}",
            "payment_failed",
            data = mapOf(
                "type" to "payment_failed",
                "bookingId" to bookingId.toString(),
                "error" to (errorMessage ?: "")
            )
        ) { email ->
            webSocketController.sendPaymentUpdate(email, bookingId, "FAILED", null)
        }
    }

    fun sendBookingCancelled(userId: Long, bookingId: Long) {
        log.info("sendBookingCancelled CALLED: userId=$userId, bookingId=$bookingId")

        val booking = getBooking(bookingId)
        if (booking == null) {
            log.error("sendBookingCancelled: booking not found for bookingId $bookingId")
            return
        }

        log.info("sendBookingCancelled: booking found, status=${booking.status}")

        val flight = getFlight(booking.flightId)
        val route = if (flight != null) {
            "${flight.departureCity} → ${flight.arrivalCity}"
        } else {
            "#$bookingId"
        }

        sendAllChannels(
            userId,
            "Booking Cancelled",
            "Your flight $route has been cancelled",
            "booking_cancelled",
            data = mapOf(
                "type" to "booking_cancelled",
                "bookingId" to bookingId.toString(),
                "flightId" to booking.flightId.toString()
            )
        ) { email ->
            log.info("sendBookingCancelled: sending WebSocket update to $email")
            webSocketController.sendBookingUpdate(email, bookingId, "CANCELLED", booking.seatNumbers)
        }

        log.info("sendBookingCancelled FINISHED: userId=$userId, bookingId=$bookingId")
    }

    fun sendBookingUpdate(userId: Long, bookingId: Long, status: String) {
        log.info("sendBookingUpdate: userId=$userId, bookingId=$bookingId, status=$status")
        sendAllChannels(
            userId,
            "Booking Update",
            "Booking #$bookingId status: $status",
            "booking_update",
            data = mapOf(
                "type" to "booking_update",
                "bookingId" to bookingId.toString(),
                "status" to status
            )
        ) { email ->
            webSocketController.sendBookingUpdate(email, bookingId, status, null)
        }
    }

    fun sendReminder(userId: Long, bookingId: Long, flight: Flight) {
        log.info("sendReminder: userId=$userId, bookingId=$bookingId")
        if (!canSend("reminder", userId, REMINDER_COOLDOWN_SECONDS)) {
            log.debug("Reminder suppressed (cooldown): user $userId")
            return
        }
        lastSentCache["reminder_${userId}"] = System.currentTimeMillis()

        val settingsOpt = settingsRepository.findByUserId(userId)
        if (settingsOpt.isPresent && !settingsOpt.get().flightReminder) {
            log.debug("Reminder suppressed (settings): user $userId")
            return
        }

        sendAllChannels(
            userId,
            "Flight Tomorrow!",
            "Your flight ${flight.departureCity} → ${flight.arrivalCity} departs at ${flight.departureTime}",
            "reminder",
            data = mapOf(
                "type" to "reminder",
                "bookingId" to bookingId.toString(),
                "flightId" to flight.flightId.toString()
            )
        ) {}
    }

    fun sendWelcomeEmail(userId: Long) {
        log.info("sendWelcomeEmail: userId=$userId")
        sendAllChannels(
            userId,
            "Welcome to SkyFlight! 🎉",
            "Start your journey with us! Book your first flight now.",
            "welcome",
            data = mapOf(
                "type" to "welcome"
            )
        ) {}
    }

    fun sendReviewReminder(userId: Long, bookingId: Long, flight: Flight) {
        log.info("sendReviewReminder: userId=$userId, bookingId=$bookingId")
        if (!canSend("review_reminder", userId, 86400)) {
            log.debug("Review reminder suppressed (cooldown): user $userId")
            return
        }
        lastSentCache["review_reminder_${userId}"] = System.currentTimeMillis()

        sendAllChannels(
            userId,
            "Rate your flight!",
            "How was your flight with ${flight.airlineName}? Share your experience!",
            "review_reminder",
            data = mapOf(
                "type" to "review_reminder",
                "bookingId" to bookingId.toString(),
                "flightId" to flight.flightId.toString()
            )
        ) {}
    }

    fun sendThankYouAfterFlight(userId: Long, bookingId: Long, flight: Flight) {
        log.info("sendThankYouAfterFlight: userId=$userId, bookingId=$bookingId")
        val settingsOpt = settingsRepository.findByUserId(userId)
        if (settingsOpt.isPresent && !settingsOpt.get().thankYouAfterFlight) {
            log.debug("Thank you suppressed (settings): user $userId")
            return
        }

        if (!canSend("thank_you", userId, 86400)) {
            log.debug("Thank you suppressed (cooldown): user $userId")
            return
        }
        lastSentCache["thank_you_${userId}"] = System.currentTimeMillis()

        sendAllChannels(
            userId,
            "Thank you for flying!",
            "We hope you enjoyed your flight with ${flight.airlineName}.",
            "thank_you",
            data = mapOf(
                "type" to "thank_you",
                "bookingId" to bookingId.toString(),
                "flightId" to flight.flightId.toString()
            )
        ) {}
    }

    fun sendPromotion(userId: Long, title: String, message: String, promoId: Long? = null, promoCode: String? = null) {
        log.info("sendPromotion: userId=$userId, title=$title")
        val data = mutableMapOf(
            "type" to "mass_promotion"
        )
        promoId?.let { data["promoId"] = it.toString() }
        promoCode?.let { data["promoCode"] = it }

        sendAllChannels(
            userId = userId,
            title = title,
            message = message,
            notificationType = "mass_promotion",
            data = data
        ) {}
    }

    fun sendReferralCode(userId: Long, referralCode: String) {
        log.info("sendReferralCode: userId=$userId, code=$referralCode")
        sendAllChannels(
            userId = userId,
            title = "Your Referral Code is Ready!",
            message = "Share your code $referralCode and earn rewards!",
            notificationType = "referral",
            data = mapOf(
                "type" to "referral",
                "referralCode" to referralCode
            )
        ) {}
    }

    private fun getFlight(flightId: Long): Flight? {
        return try {
            val optional = flightRepository.findById(flightId)
            if (optional.isPresent) optional.get() else null
        } catch (e: Exception) {
            log.error("Failed to fetch flight $flightId", e)
            null
        }
    }

    private fun getBooking(bookingId: Long): Booking? {
        return try {
            val optional = bookingRepository.findById(bookingId)
            if (optional.isPresent) optional.get() else null
        } catch (e: Exception) {
            log.error("Failed to fetch booking $bookingId", e)
            null
        }
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