package com.wheezy.server.Controller

import com.wheezy.server.DTO.*
import com.wheezy.server.Models.Referral
import com.wheezy.server.Models.ReferralCode
import com.wheezy.server.Models.ReferralStatus
import com.wheezy.server.Repository.ReferralCodeRepository
import com.wheezy.server.Repository.ReferralRepository
import com.wheezy.server.Repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/referrals")
class ReferralController(
    private val referralCodeRepository: ReferralCodeRepository,
    private val referralRepository: ReferralRepository,
    private val userRepository: UserRepository
) {

    @GetMapping("/my-code")
    fun getMyReferralCode(principal: Principal): ResponseEntity<ReferralCodeResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        var referralCode = referralCodeRepository.findByUserId(userId).orElse(null)

        if (referralCode == null) {
            val newCode = generateUniqueCode()
            referralCode = ReferralCode(
                userId = userId,
                code = newCode,
                usageCount = 0,
                maxUses = 10,
                expiresAt = LocalDateTime.now().plusYears(1)
            )
            referralCode = referralCodeRepository.save(referralCode)
        }

        val shareLink = "https://skyflightbooking.ru/register?ref=${referralCode.code}"

        return ResponseEntity.ok(
            ReferralCodeResponse(
                code = referralCode.code,
                usageCount = referralCode.usageCount,
                maxUses = referralCode.maxUses,
                isValid = referralCode.isValid(),
                shareLink = shareLink
            )
        )
    }

    @PostMapping("/apply")
    fun applyReferralCode(
        principal: Principal,
        @RequestBody request: ReferralApplyRequest
    ): ResponseEntity<ReferralApplyResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val existingReferral = referralRepository.findByReferredId(userId)
        if (existingReferral.isPresent) {
            return ResponseEntity.ok(
                ReferralApplyResponse(
                    success = false,
                    message = "You have already used a referral code",
                    discountPercent = null,
                    discountAmount = null
                )
            )
        }

        val referralCode = referralCodeRepository.findByCode(request.code).orElse(null)
        if (referralCode == null || !referralCode.isValid()) {
            return ResponseEntity.ok(
                ReferralApplyResponse(
                    success = false,
                    message = "Invalid or expired referral code",
                    discountPercent = null,
                    discountAmount = null
                )
            )
        }

        if (referralCode.userId == userId) {
            return ResponseEntity.ok(
                ReferralApplyResponse(
                    success = false,
                    message = "You cannot use your own referral code",
                    discountPercent = null,
                    discountAmount = null
                )
            )
        }

        val referral = Referral(
            referrerId = referralCode.userId,
            referredId = userId,
            referralCode = request.code,
            discountPercent = 10,
            status = ReferralStatus.PENDING
        )
        referralRepository.save(referral)

        referralCodeRepository.incrementUsageCount(request.code)

        return ResponseEntity.ok(
            ReferralApplyResponse(
                success = true,
                message = "Referral code applied! You will get 10% discount on your first booking",
                discountPercent = 10,
                discountAmount = null
            )
        )
    }

    @GetMapping("/my-referrals")
    fun getMyReferrals(principal: Principal): ResponseEntity<ReferralInfoResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val myCode = referralCodeRepository.findByUserId(userId).orElse(null)?.code ?: ""

        val referrals = referralRepository.findByReferrerId(userId)

        val referredUsers = referrals.mapNotNull { referral ->
            val referredUser = userRepository.findById(referral.referredId).orElse(null)
            if (referredUser != null) {
                ReferredUser(
                    email = referredUser.email,
                    name = referredUser.name,
                    registeredAt = referredUser.createdAt.toString(),
                    status = referral.status.name
                )
            } else null
        }

        val completedCount = referralRepository.countByReferrerIdAndStatus(userId, ReferralStatus.COMPLETED)
        val totalDiscountEarned = completedCount * 10L * 100L

        return ResponseEntity.ok(
            ReferralInfoResponse(
                myCode = myCode,
                myReferrals = referredUsers,
                totalReferrals = referrals.size,
                totalDiscountEarned = totalDiscountEarned
            )
        )
    }

    private fun generateUniqueCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var code: String
        do {
            code = (1..8).map { chars.random() }.joinToString("")
        } while (referralCodeRepository.existsByCode(code))
        return code
    }
}