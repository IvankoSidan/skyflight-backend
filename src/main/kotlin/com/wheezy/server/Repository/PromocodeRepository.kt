package com.wheezy.server.Repository

import com.wheezy.server.Models.Promocode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface PromocodeRepository : JpaRepository<Promocode, Long> {

    @Query("SELECT p FROM Promocode p WHERE p.code = :code")
    fun findByCode(@Param("code") code: String): Promocode?

    @Query("SELECT p FROM Promocode p WHERE p.code = :code AND p.isActive = true AND p.validFrom <= :now AND p.validUntil >= :now AND (p.maxUses IS NULL OR p.usedCount < p.maxUses)")
    fun findValidPromocode(
        @Param("code") code: String,
        @Param("now") now: LocalDateTime
    ): Promocode?

    @Modifying
    @Transactional
    @Query("UPDATE Promocode p SET p.usedCount = p.usedCount + 1 WHERE p.id = :id")
    fun incrementUsageCount(@Param("id") id: Long)
}