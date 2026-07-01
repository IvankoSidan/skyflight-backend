package com.wheezy.server.Controller

import com.wheezy.server.DTO.*
import com.wheezy.server.Repository.AgencyRepository
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.AgencyService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/agency")
class AgencyController(
    private val agencyService: AgencyService,
    private val agencyRepository: AgencyRepository,
    private val userRepository: UserRepository
) {

    @PostMapping("/create")
    fun createAgency(
        principal: Principal,
        @RequestBody request: AgencyCreateRequest
    ): ResponseEntity<AgencyResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        if (user.agencyId != null) {
            return ResponseEntity.badRequest().build()
        }

        val agency = agencyService.createAgency(request.name, request.email, user.email)

        return ResponseEntity.ok(AgencyResponse(
            id = agency.id,
            name = agency.name,
            slug = agency.slug,
            email = agency.email,
            subscriptionPlan = agency.subscriptionPlan
        ))
    }

    @GetMapping("/info")
    @PreAuthorize("hasRole('AGENCY_USER')")
    fun getAgencyInfo(principal: Principal): ResponseEntity<AgencyInfoResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val agencyId = user.agencyId ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        val agency = agencyRepository.findById(agencyId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val usersCount = agencyRepository.countUsersByAgencyId(agencyId)
        val limitsOk = agencyService.checkAgencyLimits(agencyId)

        return ResponseEntity.ok(AgencyInfoResponse(
            id = agency.id,
            name = agency.name,
            slug = agency.slug,
            email = agency.email,
            subscriptionPlan = agency.subscriptionPlan,
            maxUsers = agency.maxUsers,
            currentUsers = usersCount.toInt(),
            maxBookingsPerMonth = agency.maxBookingsPerMonth,
            commissionPercent = agency.commissionPercent,
            limitsOk = limitsOk,
            isSubscriptionValid = agencyService.isSubscriptionValid(agencyId)
        ))
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    fun getAgencyStatistics(principal: Principal): ResponseEntity<AgencyStatisticsResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val agencyId = user.agencyId ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val stats = agencyService.getAgencyStatistics(agencyId)

        return ResponseEntity.ok(AgencyStatisticsResponse(
            totalBookings = stats.totalBookings,
            totalRevenue = stats.totalRevenue,
            activeUsers = stats.activeUsers,
            commissionEarned = stats.commissionEarned
        ))
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    fun inviteUser(
        principal: Principal,
        @RequestBody request: AgencyInviteRequest
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val agencyId = user.agencyId ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        try {
            val invitation = agencyService.createInvitation(agencyId, request.email, request.role, user.id!!)
            return ResponseEntity.ok(mapOf(
                "message" to "Invitation sent successfully",
                "token" to invitation.token
            ))
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to create invitation")))
        }
    }

    @PostMapping("/accept/{token}")
    fun acceptInvitation(
        principal: Principal,
        @PathVariable token: String
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val success = agencyService.acceptInvitation(token, userId)

        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Invitation accepted successfully"))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid or expired invitation"))
        }
    }
}