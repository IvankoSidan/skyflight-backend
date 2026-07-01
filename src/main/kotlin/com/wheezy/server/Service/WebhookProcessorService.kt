package com.wheezy.server.Service

import com.stripe.model.PaymentIntent
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Models.PointsTransaction
import com.wheezy.server.Models.StripeWebhookEvent
import com.wheezy.server.Repository.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class WebhookProcessorService(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val flightRepository: FlightRepository,
    private val notificationSenderService: NotificationSenderService,
    private val invoiceService: InvoiceService,
    private val pendingPointsHoldRepository: PendingPointsHoldRepository,
    private val pointsTransactionRepository: PointsTransactionRepository,
    private val userPointsRepository: UserPointsRepository,
    private val eventRepository: StripeWebhookEventRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun processPaymentSuccessAsync(paymentIntent: PaymentIntent, eventId: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                log.info("Async processing payment success: ${paymentIntent.id}")
                processPaymentSuccess(paymentIntent, eventId)
                true
            } catch (e: Exception) {
                log.error("Async payment processing failed: ${paymentIntent.id}", e)
                markEventAsFailed(eventId, e.message)
                false
            }
        }
    }

    @Async
    fun processPaymentFailureAsync(paymentIntent: PaymentIntent, eventId: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                log.info("Async processing payment failure: ${paymentIntent.id}")
                processPaymentFailure(paymentIntent, eventId)
                true
            } catch (e: Exception) {
                log.error("Async payment failure processing failed: ${paymentIntent.id}", e)
                markEventAsFailed(eventId, e.message)
                false
            }
        }
    }

    @Transactional
    fun processPaymentSuccess(paymentIntent: PaymentIntent, eventId: String) {
        val stripePaymentId = paymentIntent.id
        val payment = paymentRepository.findByStripePaymentId(stripePaymentId)
            ?: throw IllegalStateException("Payment not found for Stripe ID: $stripePaymentId")

        if (payment.status == PaymentStatus.SUCCEEDED) {
            log.info("Payment already succeeded: ${payment.id}")
            markEventAsProcessed(eventId)
            return
        }

        val booking = bookingRepository.findById(payment.bookingId).orElse(null)
            ?: throw IllegalStateException("Booking not found: ${payment.bookingId}")

        val flight = flightRepository.findById(booking.flightId).orElse(null)
            ?: throw IllegalStateException("Flight not found: ${booking.flightId}")

        val user = userRepository.findById(payment.userId).orElse(null)
            ?: throw IllegalStateException("User not found: ${payment.userId}")

        payment.status = PaymentStatus.SUCCEEDED
        payment.updatedAt = LocalDateTime.now()
        paymentRepository.save(payment)

        booking.status = BookingStatus.CONFIRMED
        booking.paidAmount = BigDecimal(payment.amount).divide(BigDecimal(100))
        booking.promocodeId = payment.promocodeId
        bookingRepository.save(booking)

        // Release points hold if exists
        releasePointsHold(booking.id)

        // Send notifications (async already)
        try {
            notificationSenderService.sendBookingConfirmed(user.id!!, booking.id, payment.amount)
            notificationSenderService.sendPaymentSuccess(user.id!!, booking.id, payment.amount)
        } catch (e: Exception) {
            log.error("Failed to send notifications for booking ${booking.id}", e)
        }

        // Generate invoice
        try {
            invoiceService.generateInvoice(user.id!!, booking.id, payment.id, payment.currency)
        } catch (e: Exception) {
            log.error("Failed to generate invoice for booking ${booking.id}", e)
        }

        markEventAsProcessed(eventId)
        log.info("Payment success processed async: ${payment.id}, booking=${booking.id}")
    }

    @Transactional
    fun processPaymentFailure(paymentIntent: PaymentIntent, eventId: String) {
        val stripePaymentId = paymentIntent.id
        val payment = paymentRepository.findByStripePaymentId(stripePaymentId)
            ?: throw IllegalStateException("Payment not found for Stripe ID: $stripePaymentId")

        if (payment.status == PaymentStatus.FAILED) {
            log.info("Payment already failed: ${payment.id}")
            markEventAsProcessed(eventId)
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

        booking.status = BookingStatus.PENDING_PAYMENT
        bookingRepository.save(booking)

        // Cancel points hold if exists
        cancelPointsHold(booking.id)

        if (user != null) {
            try {
                notificationSenderService.sendPaymentFailed(
                    userId = user.id!!,
                    bookingId = booking.id,
                    errorMessage = paymentIntent.lastPaymentError?.message
                )
            } catch (e: Exception) {
                log.error("Failed to send payment failure notification", e)
            }
        }

        markEventAsProcessed(eventId)
        log.info("Payment failure processed async: ${payment.id}, booking=${booking.id}")
    }

    private fun markEventAsProcessed(eventId: String) {
        eventRepository.findById(eventId).ifPresent { event ->
            event.processed = true
            event.processedAt = LocalDateTime.now()
            eventRepository.save(event)
        }
    }

    private fun markEventAsFailed(eventId: String, error: String?) {
        eventRepository.findById(eventId).ifPresent { event ->
            event.lastError = error?.take(500)
            event.retryCount++
            eventRepository.save(event)
        }
    }

    private fun releasePointsHold(bookingId: Long) {
        try {
            val hold = pendingPointsHoldRepository.findByBookingIdAndStatus(bookingId, "ACTIVE")
            if (hold != null) {
                hold.status = "RELEASED"
                pendingPointsHoldRepository.save(hold)

                val updated = userPointsRepository.confirmPointsDeduction(hold.userId, hold.pointsHeld)
                if (updated > 0) {
                    val transaction = PointsTransaction(
                        userId = hold.userId,
                        amount = -hold.pointsHeld,
                        type = "REDEMPTION",
                        referenceId = bookingId,
                        description = "Used ${hold.pointsHeld} points for booking #$bookingId"
                    )
                    pointsTransactionRepository.save(transaction)
                    log.info("Points hold released for booking $bookingId: ${hold.pointsHeld} points")
                }
            }
        } catch (e: Exception) {
            log.error("Error releasing points hold for booking $bookingId", e)
        }
    }

    private fun cancelPointsHold(bookingId: Long) {
        try {
            val hold = pendingPointsHoldRepository.findByBookingIdAndStatus(bookingId, "ACTIVE")
            if (hold != null) {
                hold.status = "CANCELLED"
                pendingPointsHoldRepository.save(hold)
                userPointsRepository.unfreezePoints(hold.userId, hold.pointsHeld)
                log.info("Points hold cancelled for booking $bookingId: ${hold.pointsHeld} points returned")
            }
        } catch (e: Exception) {
            log.error("Error cancelling points hold for booking $bookingId", e)
        }
    }
}