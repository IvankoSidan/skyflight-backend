package com.wheezy.server.Controller

import com.wheezy.server.DTO.PromocodeRequest
import com.wheezy.server.DTO.PromocodeResponse
import com.wheezy.server.Models.Promocode
import com.wheezy.server.Repository.PromocodeRepository
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/promocodes")
class PromocodeController(
    private val promocodeRepository: PromocodeRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @PostConstruct
    fun initPromocodes() {
        try {
            val count = promocodeRepository.count()
            if (count == 0L) {
                logger.info("Seeding promocodes...")
                val promocodes = listOf(
                    Promocode(
                        code = "WELCOME10",
                        discountPercent = 10,
                        validFrom = LocalDateTime.now(),
                        validUntil = LocalDateTime.now().plusDays(30),
                        maxUses = 100,
                        minOrderAmount = 0,
                        isActive = true
                    ),
                    Promocode(
                        code = "FLY20",
                        discountPercent = 20,
                        validFrom = LocalDateTime.now(),
                        validUntil = LocalDateTime.now().plusDays(15),
                        maxUses = 50,
                        minOrderAmount = 0,
                        isActive = true
                    ),
                    Promocode(
                        code = "SKY50",
                        discountAmount = 5000L,
                        validFrom = LocalDateTime.now(),
                        validUntil = LocalDateTime.now().plusDays(7),
                        maxUses = 20,
                        minOrderAmount = 0,
                        isActive = true
                    ),
                    Promocode(
                        code = "SUMMER",
                        discountPercent = 15,
                        validFrom = LocalDateTime.now(),
                        validUntil = LocalDateTime.now().plusDays(45),
                        maxUses = 200,
                        minOrderAmount = 0,
                        isActive = true
                    ),
                    Promocode(
                        code = "TEST100",
                        discountAmount = 10000L,
                        validFrom = LocalDateTime.now(),
                        validUntil = LocalDateTime.now().plusDays(1),
                        maxUses = 5,
                        minOrderAmount = 0,
                        isActive = true
                    )
                )
                promocodeRepository.saveAll(promocodes)
                logger.info("Promocodes seeded: ${promocodeRepository.count()}")
            }
        } catch (e: Exception) {
            logger.error("Failed to seed promocodes", e)
        }
    }

    @PostMapping("/validate")
    fun validatePromocode(@RequestBody request: PromocodeRequest): ResponseEntity<PromocodeResponse> {
        val code = request.code.uppercase().trim()
        val amount = request.amount
        val currency = request.currency.uppercase()

        logger.info("Validating promocode: code=$code, amount=$amount, currency=$currency")

        var promocode: Promocode? = null
        try {
            val query = entityManager.createQuery(
                "SELECT p FROM Promocode p WHERE p.code = :code",
                Promocode::class.java
            )
            query.setParameter("code", code)
            promocode = query.resultList.firstOrNull()
        } catch (e: Exception) {
            logger.error("Error querying promocode via EntityManager", e)
        }

        if (promocode == null) {
            promocode = promocodeRepository.findByCode(code)
        }

        if (promocode == null) {
            val count = promocodeRepository.count()
            logger.warn("Promocode not found: $code. Total promocodes in DB: $count")
            return ResponseEntity.ok(
                PromocodeResponse(
                    id = null,
                    code = code,
                    discountPercent = null,
                    discountAmount = null,
                    discountedAmount = amount,
                    isValid = false,
                    message = "The promo code is invalid or expired"
                )
            )
        }

        logger.info("Promocode found: ${promocode.code}, id=${promocode.id}")

        if (!promocode.isActive) {
            logger.warn("Promocode is inactive: ${promocode.code}")
            return ResponseEntity.ok(
                PromocodeResponse(
                    id = promocode.id,
                    code = code,
                    discountPercent = promocode.discountPercent,
                    discountAmount = promocode.discountAmount,
                    discountedAmount = amount,
                    isValid = false,
                    message = "The promo code is inactive"
                )
            )
        }

        val now = LocalDateTime.now()
        if (promocode.validFrom.isAfter(now) || promocode.validUntil.isBefore(now)) {
            logger.warn("Promocode expired: ${promocode.code}")
            return ResponseEntity.ok(
                PromocodeResponse(
                    id = promocode.id,
                    code = code,
                    discountPercent = promocode.discountPercent,
                    discountAmount = promocode.discountAmount,
                    discountedAmount = amount,
                    isValid = false,
                    message = "The promo code is expired"
                )
            )
        }

        val maxUses = promocode.maxUses
        val usedCount = promocode.usedCount

        if (maxUses != null && usedCount >= maxUses) {
            logger.warn("Promocode max uses exceeded: ${promocode.code}")
            return ResponseEntity.ok(
                PromocodeResponse(
                    id = promocode.id,
                    code = code,
                    discountPercent = promocode.discountPercent,
                    discountAmount = promocode.discountAmount,
                    discountedAmount = amount,
                    isValid = false,
                    message = "The promo code has reached its usage limit"
                )
            )
        }

        val minOrderAmount = promocode.minOrderAmount
        if (minOrderAmount != null && amount < minOrderAmount) {
            logger.warn("Minimum order amount not met: $amount < $minOrderAmount")
            return ResponseEntity.ok(
                PromocodeResponse(
                    id = promocode.id,
                    code = code,
                    discountPercent = promocode.discountPercent,
                    discountAmount = promocode.discountAmount,
                    discountedAmount = amount,
                    isValid = false,
                    message = "Minimum order amount: $${minOrderAmount / 100}"
                )
            )
        }

        val discountedAmount = calculateDiscountedAmount(amount, promocode)
        logger.info("Promocode applied: ${promocode.code}, discountedAmount=$discountedAmount from $amount")

        return ResponseEntity.ok(
            PromocodeResponse(
                id = promocode.id,
                code = code,
                discountPercent = promocode.discountPercent,
                discountAmount = promocode.discountAmount,
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

    @GetMapping("/test")
    fun testPromocodes(): Map<String, Any> {
        val all = promocodeRepository.findAll()
        return mapOf(
            "count" to all.size,
            "codes" to all.map { it.code },
            "all" to all.map {
                mapOf(
                    "id" to it.id,
                    "code" to it.code,
                    "discountPercent" to it.discountPercent,
                    "discountAmount" to it.discountAmount
                )
            }
        )
    }
}
