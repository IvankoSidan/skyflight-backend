package com.wheezy.server.Repository

import com.wheezy.server.Models.AgencyRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AgencyRoleRepository : JpaRepository<AgencyRole, Long> {

    fun findByAgencyId(agencyId: Long): List<AgencyRole>

    fun findByAgencyIdAndName(agencyId: Long, name: String): Optional<AgencyRole>
}