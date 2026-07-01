package com.wheezy.server.Controller

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.EphemeralKey
import com.stripe.model.PaymentIntent
import com.stripe.param.CustomerCreateParams
import com.stripe.param.EphemeralKeyCreateParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.net.RequestOptions
import com.wheezy.server.DTO.PaymentSheetRequest
import com.wheezy.server.DTO.PaymentSheetResponse
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Enums.PaymentStatus
import com.wheezy.server.Models.Payment
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.PaymentRepository
import com.wheezy.server.Repository.PromocodeRepository
import com.wheezy.server.Repository.SavedCardRepository
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.Principal

@RestController
@RequestMapping("/api/payments")
class PaymentSheetController(
    @Value("\${stripe.api-key}") private val stripeKey: String,
    @Value("\${stripe.publishable-key}") private val publishableKey: String,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val paymentRepository: PaymentRepository,
    private val promocodeRepository: PromocodeRepository,
    private val savedCardRepository: SavedCardRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        Stripe.apiKey = stripeKey
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")
    }

    @PostMapping("/sheet")
    fun createPaymentSheet(
        @RequestBody request: PaymentSheetRequest,
        principal: Principal?
    ): ResponseEntity<PaymentSheetResponse> {
        val startTime = System.currentTimeMillis()

        try {
            val user = principal?.let { userRepository.findByEmail(it.name) }
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

            val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

            val booking = bookingRepository.findById(request.bookingId).orElse(null)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

            if (booking.userId != userId || booking.status != BookingStatus.PENDING_PAYMENT) {
                log.error("Access denied for booking: ${booking.id}")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }

            val currentFlight = flightRepository.findById(booking.flightId).orElse(null)
            if (currentFlight == null) {
                log.error("Flight not found: ${booking.flightId}")
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }

            var finalExpectedAmount = currentFlight.price
                .multiply(BigDecimal(booking.seatCount))
                .multiply(BigDecimal(100))
                .toLong()

            val promocodeId = request.promocodeId ?: booking.promocodeId
            var appliedPromocodeId: Long? = null

            if (promocodeId != null) {
                val promocode = promocodeRepository.findById(promocodeId).orElse(null)
                if (promocode != null && promocode.isActive) {
                    val discountPercent = promocode.discountPercent
                    val discountAmount = promocode.discountAmount

                    finalExpectedAmount = when {
                        discountAmount != null -> {
                            val discounted = finalExpectedAmount - discountAmount
                            discounted.coerceAtLeast(0)
                        }
                        discountPercent != null && discountPercent > 0 -> {
                            val discounted = finalExpectedAmount - (finalExpectedAmount * discountPercent / 100)
                            discounted.coerceAtLeast(0)
                        }
                        else -> finalExpectedAmount
                    }
                    appliedPromocodeId = promocodeId
                }
            }

            val amountToUse = if (request.amount > 0) {
                request.amount
            } else {
                finalExpectedAmount
            }

            var customerId = user.stripeCustomerId
            if (customerId == null) {
                try {
                    val options = RequestOptions.builder()
                        .setConnectTimeout(30000)
                        .setReadTimeout(30000)
                        .build()

                    val customerParams = CustomerCreateParams.builder()
                        .setEmail(user.email)
                        .setName(user.name ?: "User")
                        .putMetadata("user_id", userId.toString())
                        .build()

                    val customer = Customer.create(customerParams, options)
                    customerId = customer.id
                    user.stripeCustomerId = customerId
                    userRepository.save(user)
                } catch (e: Exception) {
                    log.error("Failed to create Stripe customer: ${e.message}", e)
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            }

            val ephemeralKeyParams = EphemeralKeyCreateParams.builder()
                .setStripeVersion("2023-10-16")
                .setCustomer(customerId)
                .build()

            val ephemeralKey = EphemeralKey.create(ephemeralKeyParams)

            val payment = paymentRepository.save(
                Payment(
                    userId = userId,
                    bookingId = booking.id,
                    flightId = booking.flightId,
                    amount = amountToUse,
                    currency = request.currency,
                    providerPaymentId = "temp_pi_${booking.id}_${System.currentTimeMillis()}",
                    status = PaymentStatus.PENDING,
                    promocodeId = appliedPromocodeId,
                    rememberCard = request.saveCard
                )
            )

            val intentParams = PaymentIntentCreateParams.builder()
                .setAmount(amountToUse)
                .setCurrency(request.currency.lowercase())
                .setCustomer(customerId)
                .putMetadata("payment_id", payment.id.toString())
                .putMetadata("booking_id", booking.id.toString())
                .putMetadata("user_id", userId.toString())
                .putMetadata("user_email", user.email)
                .apply {
                    if (appliedPromocodeId != null) {
                        putMetadata("promocode_id", appliedPromocodeId.toString())
                    }
                    if (request.saveCard) {
                        putMetadata("save_card", "true")
                    }
                }
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .build()

            val requestOptions = RequestOptions.builder()
                .setConnectTimeout(60000)
                .setReadTimeout(60000)
                .setIdempotencyKey("payment_${payment.id}_${System.currentTimeMillis()}")
                .build()

            val intent = PaymentIntent.create(intentParams, requestOptions)

            payment.providerPaymentId = intent.id
            payment.stripePaymentId = intent.id
            payment.status = PaymentStatus.valueOf(intent.status.uppercase())
            paymentRepository.save(payment)

            if (appliedPromocodeId != null) {
                try {
                    promocodeRepository.incrementUsageCount(appliedPromocodeId)
                } catch (e: Exception) {
                    log.error("Failed to increment promocode usage count: ${e.message}", e)
                }
            }

            return ResponseEntity.ok(
                PaymentSheetResponse(
                    paymentIntentClientSecret = intent.clientSecret,
                    ephemeralKey = ephemeralKey.secret,
                    customerId = customerId,
                    publishableKey = publishableKey
                )
            )

        } catch (e: com.stripe.exception.StripeException) {
            log.error("STRIPE ERROR: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } catch (e: Exception) {
            log.error("FATAL ERROR: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}