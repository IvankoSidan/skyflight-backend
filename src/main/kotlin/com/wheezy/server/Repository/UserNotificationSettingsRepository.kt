package com.wheezy.server.Repository

import com.wheezy.server.Models.UserNotificationSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
interface UserNotificationSettingsRepository : JpaRepository<UserNotificationSettings, Long> {

    fun findByUserId(userId: Long): Optional<UserNotificationSettings>

    fun existsByUserId(userId: Long): Boolean

    @Modifying
    @Transactional
    @Query("DELETE FROM UserNotificationSettings ns WHERE ns.userId = :userId")
    fun deleteByUserId(userId: Long)
}
