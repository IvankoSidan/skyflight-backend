package com.wheezy.server.Repository

import com.wheezy.server.Models.ReferralCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
interface ReferralCodeRepository : JpaRepository<ReferralCode, Long> {
    fun findByUserId(userId: Long): Optional<ReferralCode>
    fun findByCode(code: String): Optional<ReferralCode>
    fun existsByCode(code: String): Boolean

    @Modifying
    @Transactional
    @Query("UPDATE ReferralCode rc SET rc.usageCount = rc.usageCount + 1 WHERE rc.code = :code")
    fun incrementUsageCount(code: String)
}
