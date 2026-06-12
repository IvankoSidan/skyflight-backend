package com.wheezy.server.Repository

import com.wheezy.server.Models.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByGoogleId(googleId: String): User?
    fun existsByEmail(email: String): Boolean
}
