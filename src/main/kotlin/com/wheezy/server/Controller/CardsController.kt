package com.wheezy.server.Controller

import com.stripe.Stripe
import com.stripe.model.PaymentMethod
import com.wheezy.server.DTO.SavedCardResponse
import com.wheezy.server.Repository.SavedCardRepository
import com.wheezy.server.Repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/payments/cards")
class CardsController(
    private val savedCardRepository: SavedCardRepository,
    private val userRepository: UserRepository,
    @Value("\${stripe.api-key}") private val stripeKey: String
) {

    init {
        Stripe.apiKey = stripeKey
    }

    @GetMapping
    fun getSavedCards(principal: Principal): ResponseEntity<List<SavedCardResponse>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val cards = savedCardRepository.findByUserId(userId)

        return ResponseEntity.ok(cards.map { card ->
            SavedCardResponse(
                id = card.id,
                stripePaymentMethodId = card.stripePaymentMethodId,
                cardLast4 = card.cardLast4,
                cardBrand = card.cardBrand,
                expiryMonth = card.expiryMonth,
                expiryYear = card.expiryYear,
                isDefault = card.isDefault
            )
        })
    }

    @DeleteMapping("/{paymentMethodId}")
    fun deleteCard(
        principal: Principal,
        @PathVariable paymentMethodId: String
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val card = savedCardRepository.findByStripePaymentMethodId(paymentMethodId)
        if (card == null || card.userId != userId) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        return try {
            val paymentMethod = PaymentMethod.retrieve(paymentMethodId)
            paymentMethod.detach()
            savedCardRepository.delete(card)
            ResponseEntity.ok(hashMapOf("message" to "Card deleted successfully"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(hashMapOf("error" to (e.message ?: "Failed to delete card")))
        }
    }

    @PutMapping("/{paymentMethodId}/default")
    fun setDefaultCard(
        principal: Principal,
        @PathVariable paymentMethodId: String
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val card = savedCardRepository.findByStripePaymentMethodId(paymentMethodId)
        if (card == null || card.userId != userId) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        savedCardRepository.clearDefaultFlag(userId)
        card.isDefault = true
        savedCardRepository.save(card)

        return ResponseEntity.ok(hashMapOf("message" to "Default card updated"))
    }
}
