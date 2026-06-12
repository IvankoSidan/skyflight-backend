package com.wheezy.server.Controller

import com.wheezy.server.DTO.PromocodeRequest
import com.wheezy.server.DTO.PromocodeResponse
import com.wheezy.server.Models.Promocode
import com.wheezy.server.Repository.PromocodeRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/promocodes")
class PromocodeController(
    private val promocodeRepository: PromocodeRepository
) {
    @PostMapping("/validate")
    fun validatePromocode(@RequestBody request: PromocodeRequest): ResponseEntity<PromocodeResponse> {
        val promocode = promocodeRepository.findValidPromocode(request.code.uppercase(), LocalDateTime.now())
            ?: return ResponseEntity.ok(
                PromocodeResponse(
                    id = null,
                    code = request.code,
                    discountPercent = null,
                    discountAmount = null,
                    discountedAmount = request.amount,
                    isValid = false,
                    message = "The promo code is invalid or expired"
                )
            )

        val minOrderAmount = promocode.minOrderAmount
        val discountPercent = promocode.discountPercent
        val discountAmount = promocode.discountAmount

        if (minOrderAmount != null && request.amount < minOrderAmount) {
            return ResponseEntity.ok(
                PromocodeResponse(
                    id = promocode.id,
                    code = request.code,
                    discountPercent = discountPercent,
                    discountAmount = discountAmount,
                    discountedAmount = request.amount,
                    isValid = false,
                    message = "Minimum order amount: ${minOrderAmount / 100} ${request.currency}"
                )
            )
        }

        val discountedAmount = calculateDiscountedAmount(request.amount, promocode)

        return ResponseEntity.ok(
            PromocodeResponse(
                id = promocode.id,
                code = request.code,
                discountPercent = discountPercent,
                discountAmount = discountAmount,
                discountedAmount = discountedAmount,
                isValid = true,
                message = "Promo code successfully applied"
            )
        )
    }
    private fun calculateDiscountedAmount(originalAmount: Long, promocode: Promocode): Long {
        val discountAmount = promocode.discountAmount
        val discountPercent = promocode.discountPercent

        return when {
            discountAmount != null -> {
                val discounted = originalAmount - discountAmount
                if (discounted < 0) 0 else discounted
            }
            discountPercent != null && discountPercent > 0 -> {
                val discounted = originalAmount - (originalAmount * discountPercent / 100)
                if (discounted < 0) 0 else discounted
            }
            else -> originalAmount
        }
    }
}
