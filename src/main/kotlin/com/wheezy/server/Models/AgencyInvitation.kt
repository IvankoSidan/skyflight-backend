package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "agency_invitations")
data class AgencyInvitation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "agency_id", nullable = false)
    val agencyId: Long,

    @Column(nullable = false)
    var email: String,

    @Column(name = "role_id", nullable = false)
    var roleId: Long,

    @Column(name = "invited_by", nullable = false)
    var invitedBy: Long,

    @Column(nullable = false, unique = true)
    var token: String,

    var status: String = "PENDING",

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)