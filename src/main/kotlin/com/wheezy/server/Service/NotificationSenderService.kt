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

@Service
class NotificationSenderService(
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val fcmService: FCMService,
    private val webSocketController: WebSocketNotificationController,
    private val gmailEmailService: GmailEmailService,
    private val emailTemplate: EmailTemplateService,
    private val settingsRepository: UserNotificationSettingsRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var isSending = false

    private val sentEmailCache = mutableMapOf<String, Long>()
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
        if (!shouldSendNotification(userId, notificationType)) {
            return
        }

        if (isDuplicate(notificationType, userId, message)) {
            log.debug("Duplicate notification suppressed: $notificationType for user $userId")
            return
        }
        markSent(notificationType, userId, message)

        if (isSending) {
            return
        }

        isSending = true

        try {
            val userOpt = userRepository.findById(userId)
            if (userOpt.isPresent) {
                val user = userOpt.get()

                try {
                    webSocketAction(user.email)
                } catch (e: Exception) {
                    log.error("WebSocket failed for user ${user.id}", e)
                }

                try {
                    fcmService.sendNotificationToUser(
                        userId = userId,
                        title = title,
                        body = message,
                        data = data
                    )
                } catch (e: Exception) {
                    log.error("FCM failed for user ${user.id}", e)
                }
            }
        } finally {
            isSending = false
        }
    }

    private fun sendEmailIfNeeded(
        userId: Long,
        buildHtml: (User) -> String,
        subject: String,
        cacheKey: String? = null
    ) {
        val userOpt = userRepository.findById(userId)
        if (!userOpt.isPresent) {
            log.warn("User not found for email: userId=$userId")
            return
        }

        val user = userOpt.get()

        if (user.email.isBlank()) {
            log.error("User email is blank for userId=$userId, cannot send email")
            return
        }

        try {
            val settingsOpt = settingsRepository.findByUserId(userId)
            if (settingsOpt.isPresent && !settingsOpt.get().emailEnabled) {
                return
            }
        } catch (e: Exception) {
            log.error("Error checking email settings", e)
        }

        val key = cacheKey ?: "${subject}_${userId}"
        val lastSent = sentEmailCache[key]

        if (lastSent != null && System.currentTimeMillis() - lastSent < 300000) {
            return
        }

        try {
            val htmlContent = buildHtml(user)
            val success = gmailEmailService.sendEmail(user.email, subject, htmlContent)
            if (success) {
                sentEmailCache[key] = System.currentTimeMillis()
            } else {
                log.error("Failed to send email to user ${user.id}: $subject")
            }

            if (sentEmailCache.size > 100) {
                val iterator = sentEmailCache.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (System.currentTimeMillis() - entry.value > 3600000) {
                        iterator.remove()
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to send email to user ${user.id}", e)
        }
    }

    fun sendPointsAwarded(userId: Long, points: Int, reason: String) {
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
        val booking = getBooking(bookingId)
        val flight = booking?.let { getFlight(it.flightId) }

        if (booking == null || flight == null) {
            log.error("Cannot send booking confirmed: booking or flight not found for bookingId $bookingId")
            return
        }

        try {
            val settingsOpt = settingsRepository.findByUserId(userId)
            if (settingsOpt.isEmpty || settingsOpt.get().bookingConfirmed) {
                sendEmailIfNeeded(
                    userId = userId,
                    buildHtml = { user -> emailTemplate.bookingConfirmation(user, booking, flight, amount) },
                    subject = "Booking Confirmed - SkyFlight #$bookingId",
                    cacheKey = "booking_confirmed_${bookingId}"
                )
            }
        } catch (e: Exception) {
            log.error("Error checking email settings", e)
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
        val amountStr = String.format("%.2f", amount / 100.0)
        val booking = getBooking(bookingId)
        val flight = booking?.let { getFlight(it.flightId) }

        try {
            val settingsOpt = settingsRepository.findByUserId(userId)
            if (settingsOpt.isEmpty || settingsOpt.get().paymentSuccess) {
                sendEmailIfNeeded(
                    userId = userId,
                    buildHtml = { user -> emailTemplate.paymentSuccess(user, bookingId, amount) },
                    subject = "Payment Successful - SkyFlight #$bookingId",
                    cacheKey = "payment_success_${bookingId}"
                )
            }
        } catch (e: Exception) {
            log.error("Error checking email settings", e)
        }

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
        val booking = getBooking(bookingId)

        val shouldSendEmail = booking?.let {
            ChronoUnit.MINUTES.between(it.bookingDate, LocalDateTime.now()) > 5
        } ?: true

        if (shouldSendEmail) {
            try {
                val settingsOpt = settingsRepository.findByUserId(userId)
                if (settingsOpt.isEmpty || settingsOpt.get().bookingCancelled) {
                    sendEmailIfNeeded(
                        userId = userId,
                        buildHtml = { user -> emailTemplate.bookingCancellation(user, bookingId) },
                        subject = "Booking Cancelled - SkyFlight #$bookingId",
                        cacheKey = "booking_cancelled_${bookingId}"
                    )
                }
            } catch (e: Exception) {
                log.error("Error checking email settings", e)
            }
        }

        sendAllChannels(
            userId,
            "Booking Cancelled",
            "Your booking #$bookingId has been cancelled",
            "booking_cancelled",
            data = mapOf(
                "type" to "booking_cancelled",
                "bookingId" to bookingId.toString()
            )
        ) { email ->
            webSocketController.sendBookingUpdate(email, bookingId, "CANCELLED", null)
        }
    }

    fun sendBookingUpdate(userId: Long, bookingId: Long, status: String) {
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
        if (!canSend("reminder", userId, REMINDER_COOLDOWN_SECONDS)) {
            log.debug("Reminder suppressed (cooldown): user $userId")
            return
        }
        lastSentCache["reminder_${userId}"] = System.currentTimeMillis()

        val settingsOpt = settingsRepository.findByUserId(userId)
        if (settingsOpt.isPresent && !settingsOpt.get().flightReminder) {
            return
        }

        sendEmailIfNeeded(
            userId = userId,
            buildHtml = { user -> emailTemplate.reminder(user, bookingId, flight) },
            subject = "Reminder: Your flight is tomorrow!",
            cacheKey = "reminder_${bookingId}"
        )

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
        try {
            val settingsOpt = settingsRepository.findByUserId(userId)
            if (settingsOpt.isPresent && !settingsOpt.get().emailEnabled) {
                return
            }
        } catch (e: Exception) {
            log.error("Error checking email settings", e)
        }

        sendEmailIfNeeded(
            userId = userId,
            buildHtml = { user -> emailTemplate.welcomeEmail(user) },
            subject = "Welcome to SkyFlight!",
            cacheKey = "welcome_${userId}"
        )

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
        val settingsOpt = settingsRepository.findByUserId(userId)
        if (settingsOpt.isPresent && !settingsOpt.get().thankYouAfterFlight) {
            return
        }

        if (!canSend("thank_you", userId, 86400)) {
            log.debug("Thank you suppressed (cooldown): user $userId")
            return
        }
        lastSentCache["thank_you_${userId}"] = System.currentTimeMillis()

        sendEmailIfNeeded(
            userId = userId,
            buildHtml = { user -> emailTemplate.thankYouAfterFlight(user, bookingId, flight) },
            subject = "Thank you for flying with SkyFlight!",
            cacheKey = "thank_you_${bookingId}"
        )

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