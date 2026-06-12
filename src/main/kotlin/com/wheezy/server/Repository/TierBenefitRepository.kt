package com.wheezy.server.Repository

import com.wheezy.server.Models.TierBenefit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TierBenefitRepository : JpaRepository<TierBenefit, String> {
    fun findAllByOrderByMinPointsAsc(): List<TierBenefit>
    fun findByTier(tier: String): TierBenefit?
}
