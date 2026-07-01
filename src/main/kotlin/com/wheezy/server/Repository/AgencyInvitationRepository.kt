package com.wheezy.server.Repository

import com.wheezy.server.Models.AgencyInvitation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface AgencyInvitationRepository : JpaRepository<AgencyInvitation, Long> {

    fun findByToken(token: String): Optional<AgencyInvitation>

    fun findByAgencyIdAndEmail(agencyId: Long, email: String): List<AgencyInvitation>

    @Modifying
    @Transactional
    @Query("UPDATE AgencyInvitation ai SET ai.status = 'EXPIRED' WHERE ai.expiresAt < :now AND ai.status = 'PENDING'")
    fun expireOldInvitations(@Param("now") now: LocalDateTime): Int
}