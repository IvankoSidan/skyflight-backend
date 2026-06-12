package com.wheezy.server.Controller

import com.wheezy.server.DTO.*
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
    private val bookingRepository: BookingRepository
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
                createdAt = tx.createdAt.toString()
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
                createdAt = tx.createdAt.toString()
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
            return ResponseEntity.badRequest().body(RedeemPointsResponse(
                success = false,
                discountAmount = 0,
                remainingPoints = userPoints?.balance ?: 0,
                message = "Not enough points"
            ))
        }

        val booking = bookingRepository.findById(request.bookingId).orElse(null)
        if (booking == null || booking.userId != userId) {
            return ResponseEntity.badRequest().body(RedeemPointsResponse(
                success = false,
                discountAmount = 0,
                remainingPoints = userPoints.balance,
                message = "Invalid booking"
            ))
        }

        if (booking.status != com.wheezy.server.Enums.BookingStatus.PENDING_PAYMENT) {
            return ResponseEntity.badRequest().body(RedeemPointsResponse(
                success = false,
                discountAmount = 0,
                remainingPoints = userPoints.balance,
                message = "Booking cannot be modified"
            ))
        }

        val discountAmount = (request.points / 100) * 100L

        // Используем deductPoints
        val updated = userPointsRepository.deductPoints(userId, request.points)
        if (updated == 0) {
            return ResponseEntity.badRequest().body(RedeemPointsResponse(
                success = false,
                discountAmount = 0,
                remainingPoints = userPoints.balance,
                message = "Failed to deduct points"
            ))
        }

        val transaction = PointsTransaction(
            userId = userId,
            amount = -request.points,
            type = "REDEMPTION",
            referenceId = request.bookingId,
            description = "Used $request.points points for booking #${request.bookingId}"
        )
        pointsTransactionRepository.save(transaction)

        val newBalance = userPoints.balance - request.points

        return ResponseEntity.ok(RedeemPointsResponse(
            success = true,
            discountAmount = discountAmount,
            remainingPoints = newBalance,
            message = "Points redeemed successfully"
        ))
    }

    @GetMapping("/tiers")
    fun getTiers(): ResponseEntity<List<TierBenefit>> {
        return ResponseEntity.ok(tierBenefitRepository.findAllByOrderByMinPointsAsc())
    }
}
