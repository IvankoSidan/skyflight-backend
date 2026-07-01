package com.wheezy.server.Service

import com.wheezy.server.Models.Agency
import com.wheezy.server.Models.AgencyInvitation
import com.wheezy.server.Models.AgencyRole
import com.wheezy.server.Models.User
import com.wheezy.server.Repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class AgencyService(
    private val agencyRepository: AgencyRepository,
    private val agencyRoleRepository: AgencyRoleRepository,
    private val agencyInvitationRepository: AgencyInvitationRepository,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository
) {

    @Transactional
    fun createAgency(name: String, email: String, ownerEmail: String): Agency {
        val slug = name.lowercase()
            .replace(" ", "-")
            .replace(Regex("[^a-z0-9-]"), "")

        val agency = Agency(
            name = name,
            slug = slug,
            email = email
        )
        val savedAgency = agencyRepository.save(agency)

        val owner = userRepository.findByEmail(ownerEmail)
            ?: throw IllegalArgumentException("Owner not found: $ownerEmail")

        owner.agencyId = savedAgency.id
        owner.role = "OWNER"
        userRepository.save(owner)

        return savedAgency
    }

    fun getAgencyById(id: Long): Agency? = agencyRepository.findById(id).orElse(null)

    fun getAgencyBySlug(slug: String): Agency? = agencyRepository.findBySlug(slug).orElse(null)

    fun checkAgencyLimits(agencyId: Long): Boolean {
        val agency = agencyRepository.findById(agencyId).orElse(null) ?: return true

        val currentUsers = agencyRepository.countUsersByAgencyId(agencyId)
        if (currentUsers >= agency.maxUsers) {
            return false
        }

        val now = LocalDateTime.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = now.withDayOfMonth(now.month.length(now.toLocalDate().isLeapYear))

        val bookingsThisMonth = agencyRepository.countBookingsByAgencyIdAndDateRange(
            agencyId, startOfMonth, endOfMonth
        )

        return bookingsThisMonth < agency.maxBookingsPerMonth
    }

    fun isSubscriptionValid(agencyId: Long): Boolean {
        val agency = agencyRepository.findById(agencyId).orElse(null) ?: return false
        return agency.subscriptionValidUntil?.isAfter(LocalDateTime.now()) ?: false
    }

    @Transactional
    fun createInvitation(agencyId: Long, email: String, roleName: String, invitedBy: Long): AgencyInvitation {
        val role = agencyRoleRepository.findByAgencyIdAndName(agencyId, roleName)
            .orElseThrow { IllegalArgumentException("Role not found: $roleName") }

        val token = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusDays(7)

        val invitation = AgencyInvitation(
            agencyId = agencyId,
            email = email,
            roleId = role.id,
            invitedBy = invitedBy,
            token = token,
            expiresAt = expiresAt
        )

        return agencyInvitationRepository.save(invitation)
    }

    @Transactional
    fun acceptInvitation(token: String, userId: Long): Boolean {
        val invitation = agencyInvitationRepository.findByToken(token).orElse(null) ?: return false

        if (invitation.status != "PENDING" || invitation.expiresAt.isBefore(LocalDateTime.now())) {
            return false
        }

        val user = userRepository.findById(userId).orElse(null) ?: return false
        val role = agencyRoleRepository.findById(invitation.roleId).orElse(null) ?: return false

        user.agencyId = invitation.agencyId
        user.role = role.name
        userRepository.save(user)

        invitation.status = "ACCEPTED"
        agencyInvitationRepository.save(invitation)

        return true
    }

    fun getAgencyStatistics(agencyId: Long): AgencyStatistics {
        val totalBookings = bookingRepository.count()
        val totalRevenue = agencyRepository.sumRevenueByAgencyId(agencyId)
        val activeUsers = userRepository.countActiveUsersByAgencyId(agencyId)

        return AgencyStatistics(
            totalBookings = totalBookings,
            totalRevenue = totalRevenue,
            activeUsers = activeUsers.toInt(),
            commissionEarned = totalRevenue * 0.10
        )
    }

    data class AgencyStatistics(
        val totalBookings: Long,
        val totalRevenue: Double,
        val activeUsers: Int,
        val commissionEarned: Double
    )
}