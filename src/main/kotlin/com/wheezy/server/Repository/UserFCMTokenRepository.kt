package com.wheezy.server.Repository

import com.wheezy.server.Models.UserFCMToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface UserFCMTokenRepository : JpaRepository<UserFCMToken, Long> {
    fun findByUserId(userId: Long): List<UserFCMToken>

    fun findByToken(token: String): UserFCMToken?

    @Modifying
    @Transactional
    fun deleteByUserIdAndToken(userId: Long, token: String)

    @Modifying
    @Transactional
    fun deleteByUserId(userId: Long)

    @Modifying
    @Transactional
    fun deleteByToken(token: String)
}