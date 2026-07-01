package com.wheezy.server.Controller

import com.wheezy.server.DTO.*
import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Models.PendingPointsHold
import com.wheezy.server.Models.PointsTransaction
import com.wheezy.server.Models.TierBenefit
import com.wheezy.server.Repository.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/loyalty")
class LoyaltyController(
    private val userPointsRepository: UserPointsRepository,
    private val pointsTransactionRepository: PointsTransactionRepository,
    private val tierBenefitRepository: TierBenefitRepository,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val pendingPointsHoldRepository: PendingPointsHoldRepository
) {

    @GetMapping("/points")
    fun getPointsBalance(principal: Principal): ResponseEntity<PointsBalanceResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val userPoints = userPointsRepository.findByUserId(userId).orElse(null)
        if (userPoints == null) {
            return ResponseEntity.ok(PointsBalanceResponse(
                balance = 0,
                lifetimePoints = 0,
                tier = "BRONZE",
                nextTier = "SILVER",
                pointsToNextTier = 1000,
                cashbackPercent = 5
            ))
        }

        val allTiers = tierBenefitRepository.findAllByOrderByMinPointsAsc()
        val currentTierIndex = allTiers.indexOfFirst { it.tier == userPoints.tier }
        val nextTier = if (currentTierIndex + 1 < allTiers.size) allTiers[currentTierIndex + 1].tier else null
        val pointsToNextTier = if (nextTier != null) {
            allTiers[currentTierIndex + 1].minPoints - userPoints.lifetimePoints
        } else 0

        val currentTierBenefit = tierBenefitRepository.findByTier(userPoints.tier)

        return ResponseEntity.ok(PointsBalanceResponse(
            balance = userPoints.balance,
            lifetimePoints = userPoints.lifetimePoints,
            tier = userPoints.tier,
            nextTier = nextTier,
            pointsToNextTier = pointsToNextTier.coerceAtLeast(0),
            cashbackPercent = currentTierBenefit?.cashbackPercent ?: 5
        ))
    }

    @GetMapping("/transactions")
    fun getTransactions(principal: Principal): ResponseEntity<List<PointsTransactionResponse>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val transactions = pointsTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId)

        return ResponseEntity.ok(transactions.map { tx ->
            PointsTransactionResponse(
                id = tx.id,
                amount = tx.amount,
                type = tx.type,
                description = tx.description,
                createdAt = tx.createdAt
            )
        })
    }

    @GetMapping("/transactions/by-type")
    fun getTransactionsByType(
        principal: Principal,
        @RequestParam type: String
    ): ResponseEntity<List<PointsTransactionResponse>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val transactions = pointsTransactionRepository.findByUserIdAndType(userId, type)

        return ResponseEntity.ok(transactions.map { tx ->
            PointsTransactionResponse(
                id = tx.id,
                amount = tx.amount,
                type = tx.type,
                description = tx.description,
                createdAt = tx.createdAt
            )
        })
    }

    @GetMapping("/calculate")
    fun calculateDiscount(
        principal: Principal,
        @RequestParam amount: Long,
        @RequestParam points: Int
    ): ResponseEntity<CalculateDiscountResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val userPoints = userPointsRepository.findByUserId(userId).orElse(null)
        val availablePoints = userPoints?.balance ?: 0
        val pointsToUse = points.coerceIn(0, availablePoints)

        val discountAmount = (pointsToUse / 100) * 100L
        val finalAmount = (amount - discountAmount).coerceAtLeast(0)

        return ResponseEntity.ok(CalculateDiscountResponse(
            discountAmount = discountAmount,
            pointsUsed = pointsToUse,
            finalAmount = finalAmount
        ))
    }

    @PostMapping("/redeem")
    fun redeemPoints(
        principal: Principal,
        @RequestBody request: RedeemPointsRequest
    ): ResponseEntity<RedeemPointsResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val userPoints = userPointsRepository.findByUserId(userId).orElse(null)
        if (userPoints == null || userPoints.balance < request.points) {
            return ResponseEntity.badRequest().body(
                RedeemPointsResponse(
                    success = false,
                    discountAmount = 0,
                    remainingPoints = userPoints?.balance ?: 0,
                    message = "Not enough points"
                )
            )
        }

        val booking = bookingRepository.findById(request.bookingId).orElse(null)
        if (booking == null || booking.userId != userId) {
            return ResponseEntity.badRequest().body(
                RedeemPointsResponse(
                    success = false,
                    discountAmount = 0,
                    remainingPoints = userPoints.balance,
                    message = "Invalid booking"
                )
            )
        }

        if (booking.status != BookingStatus.PENDING_PAYMENT) {
            return ResponseEntity.badRequest().body(
                RedeemPointsResponse(
                    success = false,
                    discountAmount = 0,
                    remainingPoints = userPoints.balance,
                    message = "Booking cannot be modified"
                )
            )
        }

        val existingHold = pendingPointsHoldRepository.findByBookingIdAndStatus(booking.id, "ACTIVE")
        if (existingHold != null) {
            return ResponseEntity.badRequest().body(
                RedeemPointsResponse(
                    success = false,
                    discountAmount = 0,
                    remainingPoints = userPoints.balance,
                    message = "Points already reserved for this booking"
                )
            )
        }

        val hold = PendingPointsHold(
            userId = userId,
            bookingId = booking.id,
            pointsHeld = request.points
        )
        pendingPointsHoldRepository.save(hold)

        userPointsRepository.freezePoints(userId, request.points)

        val discountAmount = (request.points / 100) * 100L

        return ResponseEntity.ok(
            RedeemPointsResponse(
                success = true,
                discountAmount = discountAmount,
                remainingPoints = userPoints.balance - request.points,
                message = "Points reserved. Will be applied after successful payment."
            )
        )
    }

    @GetMapping("/tiers")
    fun getTiers(): ResponseEntity<List<TierBenefit>> {
        return ResponseEntity.ok(tierBenefitRepository.findAllByOrderByMinPointsAsc())
    }
}