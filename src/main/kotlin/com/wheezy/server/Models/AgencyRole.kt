package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "agency_roles")
data class AgencyRole(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "agency_id", nullable = false)
    val agencyId: Long,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT[]")
    var permissions: Array<String> = emptyArray(),

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)