package com.wheezy.server.Repository

import com.wheezy.server.Models.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): User?

    fun findByGoogleId(googleId: String): User?

    fun existsByEmail(email: String): Boolean

    @Query("SELECT u FROM User u WHERE u.agencyId = :agencyId")
    fun findAllByAgencyId(@Param("agencyId") agencyId: Long): List<User>

    @Query("SELECT COUNT(u) FROM User u WHERE u.agencyId = :agencyId AND u.isEnabled = true")
    fun countActiveUsersByAgencyId(@Param("agencyId") agencyId: Long): Long

    @Query("SELECT u FROM User u WHERE u.agencyId = :agencyId AND u.role = 'OWNER'")
    fun findOwnerByAgencyId(@Param("agencyId") agencyId: Long): User?
}