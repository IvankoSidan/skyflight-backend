package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "agency_domains")
data class AgencyDomain(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "agency_id", nullable = false)
    val agencyId: Long,

    @Column(nullable = false)
    var domain: String,

    @Column(name = "is_primary")
    var isPrimary: Boolean = false,

    @Column(name = "is_verified")
    var isVerified: Boolean = false,

    @Column(name = "verification_token")
    var verificationToken: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)