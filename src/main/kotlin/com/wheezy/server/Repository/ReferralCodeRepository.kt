package com.wheezy.server.Repository

import com.wheezy.server.Models.ReferralCode
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
interface ReferralCodeRepository : JpaRepository<ReferralCode, Long> {
    fun findByUserId(userId: Long): Optional<ReferralCode>
    fun findByCode(code: String): Optional<ReferralCode>
    fun existsByCode(code: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rc FROM ReferralCode rc WHERE rc.code = :code")
    fun findByCodeWithLock(@Param("code") code: String): Optional<ReferralCode>

    @Modifying
    @Transactional
    @Query("UPDATE ReferralCode rc SET rc.usageCount = rc.usageCount + 1 WHERE rc.code = :code")
    fun incrementUsageCount(code: String)
}
