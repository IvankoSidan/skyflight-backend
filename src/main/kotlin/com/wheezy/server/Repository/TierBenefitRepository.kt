package com.wheezy.server.Repository

import com.wheezy.server.Models.TierBenefit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TierBenefitRepository : JpaRepository<TierBenefit, String> {

    fun findAllByOrderByMinPointsAsc(): List<TierBenefit>

    @Query("SELECT tb FROM TierBenefit tb WHERE tb.tier = :tier")
    fun findByTier(@Param("tier") tier: String): TierBenefit?
}