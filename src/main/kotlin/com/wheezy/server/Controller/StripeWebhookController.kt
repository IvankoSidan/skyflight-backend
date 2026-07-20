package com.wheezy.server.Controller

import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.model.PaymentMethod
import com.stripe.net.Webhook
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Models.PointsTransaction
import com.wheezy.server.Models.SavedCard
import com.wheezy.server.Models.StripeWebhookEvent
import com.wheezy.server.Models.UserPoints
import com.wheezy.server.Repository.*
import com.wheezy.server.Service.BookingService
import com.wheezy.server.Service.InvoiceService
import com.wheezy.server.Service.NotificationSenderService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/stripe/webhook")
class StripeWebhookController(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val notificationSenderService: NotificationSenderService,
    private val eventRepository: StripeWebhookEventRepository,
    private val invoiceService: InvoiceService,
    private val pendingPointsHoldRepository: PendingPointsHoldRepository,
    private val pointsTransactionRepository: PointsTransactionRepository,
    private val userPointsRepository: UserPointsRepository,
    private val savedCardRepository: SavedCardRepository,
    private val tierBenefitRepository: TierBenefitRepository,
    private val bookingService: BookingService,
    @Value("\${stripe.webhook.secret}") private val endpointSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @Transactional
    fun handleWebhook(request: HttpServletRequest): ResponseEntity<String> {
        val payload = request.reader.readText()
        val sigHeader = request.getHeader("Stripe-Signature")

        val event: Event = try {
            Webhook.constructEvent(payload, sigHeader, endpointSecret)
        } catch (e: Exception) {
            log.warn("Webhook verification failed: ${e.message}")
            return ResponseEntity.badRequest().body("Invalid signature")
        }

        val existingEvent = eventRepository.findById(event.id).orElse(null)
        if (existingEvent != null) {
            if (existingEvent.processed) {
                return ResponseEntity.ok("already_processed")
            } else {
                log.warn("Webhook in progress, retrying: ${event.id}")
                Thread.sleep(1000)
                val retryEvent = eventRepository.findById(event.id).orElse(null)
                if (retryEvent?.processed == true) {
                    return ResponseEntity.ok("already_processed_after_retry")
                }
            }
        }

        var webhookEvent = StripeWebhookEvent(
            eventId = event.id,
            type = event.type,
            processed = false,
            payload = payload
        )

        try {
            eventRepository.save(webhookEvent)
        } catch (e: Exception) {
            log.error("Failed to save webhook event, possible duplicate: ${event.id}", e)
            val existing = eventRepository.findById(event.id).orElse(null)
            if (existing?.processed == true) {
                return ResponseEntity.ok("already_processed_concurrent")
            }
            existing?.let {
                it.retryCount = (it.retryCount ?: 0) + 1
                eventRepository.save(it)
                webhookEvent = webhookEvent.copy(
                    eventId = event.id,
                    processed = false
                )
            }
        }

        try {
            when (event.type) {
                "payment_intent.succeeded" -> {
                    val paymentIntent = event.dataObjectDeserializer.`object`.orElse(null) as? PaymentIntent
                    if (paymentIntent != null) {
                        handlePaymentSuccess(paymentIntent)
                    } else {
                        log.error("Failed to parse payment_intent from event: ${event.id}")
                    }
                }
                "payment_intent.payment_failed" -> {
                    val paymentIntent = event.dataObjectDeserializer.`object`.orElse(null) as? PaymentIntent
                    if (paymentIntent != null) {
                        handlePaymentFailure(paymentIntent)
                    } else {
                        log.error("Failed to parse payment_intent from event: ${event.id}")
                    }
                }
                else -> {}
            }

            val finalEvent = eventRepository.findById(event.id).orElse(null)
            finalEvent?.let {
                it.processed = true
                it.processedAt = LocalDateTime.now()
                eventRepository.save(it)
            }

        } catch (e: Exception) {
            log.error("Failed to process webhook: ${event.id}", e)
            val failedEvent = eventRepository.findById(event.id).orElse(null)
            failedEvent?.let {
                it.lastError = e.message?.take(500)
                it.retryCount = (it.retryCount ?: 0) + 1
                eventRepository.save(it)
            }
            return ResponseEntity.ok("will_retry_later")
        }

        return ResponseEntity.ok("success")
    }

    @Transactional
    fun handlePaymentSuccess(paymentIntent: PaymentIntent) {
        log.info("Processing payment success: ${paymentIntent.id}")
        val stripePaymentId = paymentIntent.id

        var payment = paymentRepository.findByStripePaymentId(stripePaymentId)

        if (payment == null) {
            val metadata = paymentIntent.metadata
            val paymentIdFromMetadata = metadata?.get("payment_id")?.toLongOrNull()
            if (paymentIdFromMetadata != null) {
                payment = paymentRepository.findById(paymentIdFromMetadata).orElse(null)
                if (payment != null) {
                    payment.stripePaymentId = stripePaymentId
                    payment.providerPaymentId = stripePaymentId
                    payment = paymentRepository.save(payment)
                }
            }
        }

        if (payment == null) {
            log.error("Payment not found for Stripe ID: $stripePaymentId")
            val metadata = paymentIntent.metadata
            val bookingId = metadata?.get("booking_id")?.toLongOrNull()
            val userId = metadata?.get("user_id")?.toLongOrNull()

            if (bookingId != null && userId != null) {
                val newPayment = com.wheezy.server.Models.Payment(
                    userId = userId,
                    bookingId = bookingId,
                    flightId = 0,
                    amount = paymentIntent.amount,
                    currency = paymentIntent.currency,
                    providerPaymentId = stripePaymentId,
                    status = PaymentStatus.SUCCEEDED,
                    stripePaymentId = stripePaymentId
                )
                payment = paymentRepository.save(newPayment)
            } else {
                throw IllegalStateException("Payment not found and cannot be created from metadata")
            }
        }

        if (payment.status == PaymentStatus.SUCCEEDED) {
            log.info("Payment already succeeded: ${payment.id}")
            return
        }

        val booking = bookingRepository.findById(payment.bookingId).orElse(null)
            ?: throw IllegalStateException("Booking not found: ${payment.bookingId}")

        val user = userRepository.findById(payment.userId).orElse(null)
            ?: throw IllegalStateException("User not found: ${payment.userId}")

        payment.status = PaymentStatus.SUCCEEDED
        payment.updatedAt = LocalDateTime.now()
        payment.stripePaymentId = stripePaymentId
        payment.providerPaymentId = stripePaymentId
        paymentRepository.save(payment)
        log.info("Payment updated: ${payment.id}")

        booking.status = BookingStatus.PAID
        booking.paidAmount = BigDecimal(payment.amount).divide(BigDecimal(100))
        booking.promocodeId = payment.promocodeId
        bookingRepository.save(booking)
        log.info("Booking updated: ${booking.id}, status=PAID")

        user.id?.let { userId ->
            val amountInUsd = payment.amount / 100

            val points = amountInUsd.toInt()
            if (points > 0) {
                var userPoints = userPointsRepository.findByUserId(userId).orElse(null)
                if (userPoints == null) {
                    userPoints = UserPoints(
                        userId = userId,
                        balance = points,
                        lifetimePoints = points,
                        tier = calculateTier(points),
                        frozenBalance = 0,
                        updatedAt = LocalDateTime.now()
                    )
                    userPointsRepository.save(userPoints)
                    log.info("Created new user points: userId=$userId, points=$points")
                } else {
                    userPointsRepository.addPointsWithTierUpdate(userId, points)
                    log.info("Added points to user: userId=$userId, points=$points")
                }

                val transaction = PointsTransaction(
                    userId = userId,
                    amount = points,
                    type = "BOOKING",
                    referenceId = booking.id,
                    description = "Flight booking #${booking.id} - $points points earned"
                )
                pointsTransactionRepository.save(transaction)
                log.info("Points transaction saved: userId=$userId, points=$points")

                notificationSenderService.sendPointsAwarded(userId, points, "Flight booking #${booking.id}")
            }

            val userPoints = userPointsRepository.findByUserId(userId).orElse(null)
            val tier = userPoints?.tier ?: "BRONZE"
            val tierBenefit = tierBenefitRepository.findByTier(tier)
            val cashbackPercent = tierBenefit?.cashbackPercent ?: 0

            if (cashbackPercent > 0) {
                val cashbackPoints = (amountInUsd * cashbackPercent / 100).toInt()
                if (cashbackPoints > 0) {
                    userPointsRepository.addPointsWithTierUpdate(userId, cashbackPoints)
                    val cashbackTransaction = PointsTransaction(
                        userId = userId,
                        amount = cashbackPoints,
                        type = "CASHBACK",
                        referenceId = booking.id,
                        description = "$cashbackPercent% cashback for booking #${booking.id}"
                    )
                    pointsTransactionRepository.save(cashbackTransaction)
                    notificationSenderService.sendPointsAwarded(
                        userId = userId,
                        points = cashbackPoints,
                        reason = "$cashbackPercent% cashback for booking #${booking.id}"
                    )
                    log.info("Cashback: $cashbackPoints points awarded to user $userId ($tier tier)")
                }
            }
        }

        try {
            val paymentMethodId = paymentIntent.paymentMethod
            if (paymentMethodId != null && payment.rememberCard) {
                user.id?.let { userId ->
                    val paymentMethod = PaymentMethod.retrieve(paymentMethodId)
                    val card = paymentMethod.card
                    if (card != null) {
                        val existingCard = savedCardRepository.findByStripePaymentMethodId(paymentMethodId)
                        if (existingCard == null) {
                            val savedCard = SavedCard(
                                userId = userId,
                                stripePaymentMethodId = paymentMethodId,
                                cardLast4 = card.last4,
                                cardBrand = card.brand,
                                expiryMonth = card.expMonth.toInt(),
                                expiryYear = card.expYear.toInt(),
                                isDefault = false
                            )
                            savedCardRepository.save(savedCard)
                            log.info("Card saved: userId=$userId, last4=${card.last4}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to save card", e)
        }

        releasePointsHold(booking.id)

        user.id?.let { userId ->
            try {
                notificationSenderService.sendBookingConfirmed(userId, booking.id, payment.amount)
                notificationSenderService.sendPaymentSuccess(userId, booking.id, payment.amount)
            } catch (e: Exception) {
                log.error("Failed to send notifications", e)
            }
        }

        user.id?.let { userId ->
            try {
                val existingInvoice = invoiceService.getInvoiceByBookingId(userId, booking.id)
                if (existingInvoice == null) {
                    invoiceService.generateInvoice(
                        userId = userId,
                        bookingId = booking.id,
                        paymentId = payment.id,
                        currency = payment.currency
                    )
                    log.info("Invoice generated: bookingId=${booking.id}")
                }
            } catch (e: Exception) {
                log.error("Failed to generate invoice", e)
            }
        }

        log.info("Payment success processed: ${payment.id}, booking=${booking.id}")
    }

    private fun calculateTier(lifetimePoints: Int): String {
        val allTiers = tierBenefitRepository.findAllByOrderByMinPointsAsc()
        return allTiers.lastOrNull { lifetimePoints >= it.minPoints }?.tier ?: "BRONZE"
    }

    private fun releasePointsHold(bookingId: Long) {
        log.info("Releasing points hold for booking $bookingId")

        try {
            val hold = pendingPointsHoldRepository.findByBookingIdAndStatus(bookingId, "ACTIVE")
            if (hold != null) {
                log.info("Hold found: bookingId=$bookingId, userId=${hold.userId}, points=${hold.pointsHeld}")

                hold.status = "RELEASED"
                pendingPointsHoldRepository.save(hold)

                val userPoints = userPointsRepository.findByUserId(hold.userId).orElse(null)
                if (userPoints != null && userPoints.balance >= hold.pointsHeld) {
                    val updated = userPointsRepository.confirmPointsDeductionWithTierUpdate(hold.userId, hold.pointsHeld)
                    if (updated > 0) {
                        val transaction = PointsTransaction(
                            userId = hold.userId,
                            amount = -hold.pointsHeld,
                            type = "REDEMPTION",
                            referenceId = bookingId,
                            description = "Used ${hold.pointsHeld} points for booking #$bookingId"
                        )
                        pointsTransactionRepository.save(transaction)
                        log.info("Points deducted: ${hold.pointsHeld} from user ${hold.userId}")
                        notificationSenderService.sendPointsDeducted(hold.userId, hold.pointsHeld, "Booking #$bookingId")
                    } else {
                        log.warn("Failed to deduct points: user=${hold.userId}, points=${hold.pointsHeld}")
                    }
                } else {
                    log.warn("Insufficient balance: user=${hold.userId}, balance=${userPoints?.balance}, needed=${hold.pointsHeld}")
                }
            } else {
                log.warn("No active hold found for booking $bookingId")
            }
        } catch (e: Exception) {
            log.error("Error releasing points hold for booking $bookingId", e)
        }
    }

    private fun cancelPointsHold(bookingId: Long) {
        log.info("Cancelling points hold for booking $bookingId")

        try {
            val hold = pendingPointsHoldRepository.findByBookingIdAndStatus(bookingId, "ACTIVE")
            if (hold != null) {
                hold.status = "CANCELLED"
                pendingPointsHoldRepository.save(hold)
                userPointsRepository.unfreezePoints(hold.userId, hold.pointsHeld)
                notificationSenderService.sendPointsReturned(hold.userId, hold.pointsHeld, "Booking #$bookingId")
                log.info("Points hold cancelled: bookingId=$bookingId, userId=${hold.userId}, points=${hold.pointsHeld}")
            }
        } catch (e: Exception) {
            log.error("Error cancelling points hold", e)
        }
    }

    @Transactional
    fun handlePaymentFailure(paymentIntent: PaymentIntent) {
        log.info("Processing payment failure: ${paymentIntent.id}")

        val stripePaymentId = paymentIntent.id
        val payment = paymentRepository.findByStripePaymentId(stripePaymentId)
            ?: throw IllegalStateException("Payment not found for Stripe ID: $stripePaymentId")

        if (payment.status == PaymentStatus.FAILED) {
            log.info("Payment already failed: ${payment.id}")
            return
        }

        val booking = bookingRepository.findById(payment.bookingId).orElse(null)
            ?: throw IllegalStateException("Booking not found: ${payment.bookingId}")

        val user = userRepository.findById(payment.userId).orElse(null)

        payment.status = PaymentStatus.FAILED
        payment.failureCode = paymentIntent.lastPaymentError?.code
        payment.failureMessage = paymentIntent.lastPaymentError?.message
        payment.updatedAt = LocalDateTime.now()
        paymentRepository.save(payment)
        log.info("Payment marked as failed: ${payment.id}")

        booking.status = BookingStatus.PENDING_PAYMENT
        bookingRepository.save(booking)
        log.info("Booking status reset to PENDING_PAYMENT: ${booking.id}")

        cancelPointsHold(booking.id)

        try {
            bookingService.releaseSeats(booking)
            log.info("Seats released for booking ${booking.id} after failed payment")
        } catch (e: Exception) {
            log.error("Failed to release seats for booking ${booking.id}", e)
        }

        user?.let { nonNullUser ->
            nonNullUser.id?.let { userId ->
                try {
                    notificationSenderService.sendPaymentFailed(
                        userId = userId,
                        bookingId = booking.id,
                        errorMessage = paymentIntent.lastPaymentError?.message
                    )
                } catch (e: Exception) {
                    log.error("Failed to send payment failure notification", e)
                }
            }
        }

        log.info("Payment failure processed: ${payment.id}, booking=${booking.id}")
    }
}