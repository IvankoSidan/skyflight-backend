package com.wheezy.server.Service

import com.stripe.Stripe
import com.stripe.model.Refund
import com.stripe.param.RefundCreateParams
import com.stripe.net.RequestOptions
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Models.Booking
import com.wheezy.server.Models.Payment
import com.wheezy.server.Repository.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository,
    private val flightRepository: FlightRepository,
    private val notificationSenderService: NotificationSenderService,
    @Value("\${stripe.api-key}") stripeKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        Stripe.apiKey = stripeKey
    }

    @Transactional
    fun refundBooking(bookingId: Long, userEmail: String): Boolean {
        val user = userRepository.findByEmail(userEmail)
            ?: throw IllegalArgumentException("User not found: $userEmail")

        val userId = user.id ?: throw IllegalStateException("User ID is null")

        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { IllegalArgumentException("Booking not found: $bookingId") }

        val payments = paymentRepository.findByBookingId(booking.id)
        val payment = payments.firstOrNull()

        if (payment == null) {
            booking.status = BookingStatus.CANCELED
            bookingRepository.save(booking)
            notificationSenderService.sendBookingUpdate(userId, booking.id, "CANCELLED")
            return true
        }

        if (payment.status != PaymentStatus.SUCCEEDED) {
            booking.status = BookingStatus.CANCELED
            bookingRepository.save(booking)
            notificationSenderService.sendBookingUpdate(userId, booking.id, "CANCELLED")
            return true
        }

        if (payment.status == PaymentStatus.REFUNDED) {
            booking.status = BookingStatus.CANCELED
            bookingRepository.save(booking)
            return true
        }

        return try {
            val providerId = payment.providerPaymentId
            val idempotencyKey = "refund_${payment.id}_${System.currentTimeMillis()}"

            val params = if (providerId.startsWith("pi_")) {
                RefundCreateParams.builder()
                    .setPaymentIntent(providerId)
                    .setAmount(payment.amount)
                    .putMetadata("payment_id", payment.id.toString())
                    .putMetadata("booking_id", booking.id.toString())
                    .build()
            } else {
                RefundCreateParams.builder()
                    .setCharge(providerId)
                    .setAmount(payment.amount)
                    .putMetadata("payment_id", payment.id.toString())
                    .putMetadata("booking_id", booking.id.toString())
                    .build()
            }

            val requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build()

            val refund = Refund.create(params, requestOptions)

            payment.status = PaymentStatus.REFUNDED
            payment.refundId = refund.id
            paymentRepository.save(payment)

            booking.status = BookingStatus.CANCELED
            booking.canceledAt = LocalDateTime.now()
            bookingRepository.save(booking)

            notificationSenderService.sendBookingUpdate(userId, booking.id, "CANCELLED")

            true

        } catch (e: com.stripe.exception.InvalidRequestException) {
            when {
                e.code == "charge_already_refunded" -> {
                    payment.status = PaymentStatus.REFUNDED
                    paymentRepository.save(payment)
                    booking.status = BookingStatus.CANCELED
                    bookingRepository.save(booking)
                    true
                }
                e.code?.contains("no_successful_charge") == true -> {
                    payment.status = PaymentStatus.CANCELED
                    paymentRepository.save(payment)
                    booking.status = BookingStatus.CANCELED
                    bookingRepository.save(booking)
                    notificationSenderService.sendBookingUpdate(userId, booking.id, "CANCELLED")
                    true
                }
                else -> {
                    log.error("Error creating refund", e)
                    false
                }
            }
        } catch (e: Exception) {
            log.error("Error creating refund", e)
            false
        }
    }
}