package com.wheezy.server.Controller

import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.net.Webhook
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Models.StripeWebhookEvent
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.PaymentRepository
import com.wheezy.server.Repository.StripeWebhookEventRepository
import com.wheezy.server.Repository.UserRepository
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
    private val flightRepository: FlightRepository,
    private val notificationSenderService: NotificationSenderService,
    private val eventRepository: StripeWebhookEventRepository,
    private val invoiceService: InvoiceService,
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

        if (eventRepository.existsById(event.id)) {
            log.info("Duplicate webhook: ${event.id}")
            return ResponseEntity.ok("duplicate_ignored")
        }

        val webhookEvent = StripeWebhookEvent(
            eventId = event.id,
            type = event.type,
            processed = false,
            payload = payload
        )
        eventRepository.save(webhookEvent)

        try {
            when (event.type) {
                "payment_intent.succeeded" -> {
                    val paymentIntent = event.dataObjectDeserializer.`object`.orElse(null) as? PaymentIntent
                    if (paymentIntent != null) {
                        handlePaymentSuccess(paymentIntent)
                    }
                }
                "payment_intent.payment_failed" -> {
                    val paymentIntent = event.dataObjectDeserializer.`object`.orElse(null) as? PaymentIntent
                    if (paymentIntent != null) {
                        handlePaymentFailure(paymentIntent)
                    }
                }
                else -> log.debug("Ignored event: ${event.type}")
            }

            webhookEvent.processed = true
            webhookEvent.processedAt = LocalDateTime.now()
            eventRepository.save(webhookEvent)

        } catch (e: Exception) {
            log.error("Failed to process webhook: ${event.id}", e)
            webhookEvent.lastError = e.message?.take(500)
            webhookEvent.retryCount++
            eventRepository.save(webhookEvent)
            return ResponseEntity.ok("will_retry_later")
        }

        return ResponseEntity.ok("success")
    }

    private fun handlePaymentSuccess(paymentIntent: PaymentIntent) {
        val stripePaymentId = paymentIntent.id
        val payment = paymentRepository.findByStripePaymentId(stripePaymentId)
            ?: throw IllegalStateException("Payment not found for Stripe ID: $stripePaymentId")

        if (payment.status == PaymentStatus.SUCCEEDED) {
            log.info("Payment already succeeded: ${payment.id}")
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

        val promocodeId = payment.promocodeId
        booking.promocodeId = promocodeId
        bookingRepository.save(booking)

        try {
            notificationSenderService.sendBookingConfirmed(
                userId = user.id!!,
                bookingId = booking.id,
                amount = payment.amount
            )
            log.info("Booking confirmed notification sent for booking ${booking.id}")
        } catch (e: Exception) {
            log.error("Failed to send booking confirmed notification", e)
        }

        try {
            notificationSenderService.sendPaymentSuccess(
                userId = user.id!!,
                bookingId = booking.id,
                amount = payment.amount
            )
            log.info("Payment success notification sent for booking ${booking.id}")
        } catch (e: Exception) {
            log.error("Failed to send payment success notification", e)
        }

        try {
            invoiceService.generateInvoice(
                userId = user.id!!,
                bookingId = booking.id,
                paymentId = payment.id,
                currency = payment.currency
            )
            log.info("Invoice generated for booking ${booking.id}")
        } catch (e: Exception) {
            log.error("Failed to generate invoice for booking ${booking.id}", e)
        }

        log.info("Payment success processed: ${payment.id}, booking=${booking.id}")
    }

    private fun handlePaymentFailure(paymentIntent: PaymentIntent) {
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

        booking.status = BookingStatus.PENDING_PAYMENT
        bookingRepository.save(booking)

        if (user != null) {
            try {
                notificationSenderService.sendPaymentFailed(
                    userId = user.id!!,
                    bookingId = booking.id,
                    errorMessage = paymentIntent.lastPaymentError?.message
                )
                log.info("Payment failure notification sent for booking ${booking.id}")
            } catch (e: Exception) {
                log.error("Failed to send payment failure notification", e)
            }
        }

        log.info("Payment failure processed: ${payment.id}, booking=${booking.id}")
    }
}
