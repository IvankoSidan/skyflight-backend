package com.wheezy.server.Repository

import com.wheezy.server.Models.Referral
import com.wheezy.server.Models.ReferralStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ReferralRepository : JpaRepository<Referral, Long> {
    fun findByReferrerId(referrerId: Long): List<Referral>
    fun findByReferredId(referredId: Long): Optional<Referral>
    fun findByReferralCode(code: String): List<Referral>
    fun countByReferrerId(referrerId: Long): Int
    fun countByReferrerIdAndStatus(referrerId: Long, status: ReferralStatus): Int
}
