package com.wheezy.server.Repository

import com.wheezy.server.Models.SavedCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface SavedCardRepository : JpaRepository<SavedCard, Long> {
    fun findByUserId(userId: Long): List<SavedCard>
    fun findByStripePaymentMethodId(paymentMethodId: String): SavedCard?

    @Modifying
    @Transactional
    @Query("UPDATE SavedCard sc SET sc.isDefault = false WHERE sc.userId = :userId")
    fun clearDefaultFlag(userId: Long)
}