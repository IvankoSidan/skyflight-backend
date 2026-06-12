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
import com.wheezy.server.Repository.PaymentRepository
import com.wheezy.server.Repository.PromocodeRepository
import com.wheezy.server.Repository.SavedCardRepository
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/payments")
class PaymentSheetController(
    @Value("\${stripe.api-key}") private val stripeKey: String,
    @Value("\${stripe.publishable-key}") private val publishableKey: String,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
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
        log.info("🚀 START: Creating Payment Sheet for bookingId: ${request.bookingId}, amount: ${request.amount}")

        try {
            val user = principal?.let { userRepository.findByEmail(it.name) }
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

            val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            log.info("✅ User found: ${user.email} (id=$userId)")

            val booking = bookingRepository.findById(request.bookingId).orElse(null)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

            if (booking.userId != userId || booking.status != BookingStatus.PENDING_PAYMENT) {
                log.error("❌ Access denied for booking: ${booking.id}")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
            log.info("✅ Booking verified: id=${booking.id}, status=${booking.status}")

            if (request.promocodeId != null) {
                val promocode = promocodeRepository.findById(request.promocodeId).orElse(null)
                if (promocode == null || !promocode.isActive) {
                    log.error("❌ Invalid promocode id: ${request.promocodeId}")
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
                }

                val minOrderAmount = promocode.minOrderAmount
                if (minOrderAmount != null && request.amount < minOrderAmount) {
                    log.error("❌ Minimum order amount not met: ${request.amount} < $minOrderAmount")
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
                }

                log.info("✅ Promocode validated: ${promocode.code}")
            }

            var customerId = user.stripeCustomerId
            if (customerId == null) {
                log.info("🔄 Creating new Stripe customer...")
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
                    log.info("✅ Created new Stripe customer: $customerId")
                } catch (e: Exception) {
                    log.error("❌ Failed to create Stripe customer: ${e.message}", e)
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            } else {
                log.info("✅ Existing Stripe customer: $customerId")
            }

            log.info("🔑 Creating ephemeral key...")
            val ephemeralKeyParams = EphemeralKeyCreateParams.builder()
                .setStripeVersion("2023-10-16")
                .setCustomer(customerId)
                .build()

            val ephemeralKey = EphemeralKey.create(ephemeralKeyParams)
            log.info("✅ Ephemeral key created")

            val payment = paymentRepository.save(
                Payment(
                    userId = userId,
                    bookingId = booking.id,
                    flightId = booking.flightId,
                    amount = request.amount,
                    currency = request.currency,
                    providerPaymentId = "temp_pi_${booking.id}_${System.currentTimeMillis()}",
                    status = PaymentStatus.PENDING,
                    promocodeId = request.promocodeId
                )
            )
            log.info("✅ Payment saved: id=${payment.id}")

            val intentParams = PaymentIntentCreateParams.builder()
                .setAmount(request.amount)
                .setCurrency(request.currency.lowercase())
                .setCustomer(customerId)
                .putMetadata("payment_id", payment.id.toString())
                .putMetadata("booking_id", booking.id.toString())
                .putMetadata("user_id", userId.toString())
                .putMetadata("user_email", user.email)
                .apply {
                    if (request.promocodeId != null) {
                        putMetadata("promocode_id", request.promocodeId.toString())
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

            log.info("🔗 Calling Stripe API to create PaymentIntent (timeout: 60s)...")
            val intent = PaymentIntent.create(intentParams, requestOptions)
            val elapsed = System.currentTimeMillis() - startTime
            log.info("✅ PaymentIntent created in ${elapsed}ms: ${intent.id}")

            payment.providerPaymentId = intent.id
            payment.stripePaymentId = intent.id
            payment.status = PaymentStatus.valueOf(intent.status.uppercase())
            paymentRepository.save(payment)

            if (request.promocodeId != null) {
                try {
                    promocodeRepository.incrementUsageCount(request.promocodeId)
                    log.info("✅ Promocode usage count incremented for id: ${request.promocodeId}")
                } catch (e: Exception) {
                    log.error("⚠️ Failed to increment promocode usage count: ${e.message}", e)
                }
            }

            log.info("✅✅✅ SUCCESS! Total time: ${System.currentTimeMillis() - startTime}ms")

            return ResponseEntity.ok(
                PaymentSheetResponse(
                    paymentIntentClientSecret = intent.clientSecret,
                    ephemeralKey = ephemeralKey.secret,
                    customerId = customerId,
                    publishableKey = publishableKey
                )
            )

        } catch (e: com.stripe.exception.StripeException) {
            val elapsed = System.currentTimeMillis() - startTime
            log.error("❌❌❌ STRIPE ERROR after ${elapsed}ms: ${e.message}", e)
            log.error("Stripe error code: ${e.code}, status code: ${e.statusCode}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            log.error("❌❌❌ FATAL ERROR after ${elapsed}ms: ${e.message}", e)
            e.printStackTrace()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}
