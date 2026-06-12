package com.wheezy.server.Repository

import com.wheezy.server.Models.UserPoints
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
interface UserPointsRepository : JpaRepository<UserPoints, Long> {
    fun findByUserId(userId: Long): Optional<UserPoints>

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.balance = up.balance + :points, up.lifetimePoints = up.lifetimePoints + :points WHERE up.userId = :userId")
    fun addPoints(userId: Long, points: Int)

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.balance = up.balance - :points WHERE up.userId = :userId AND up.balance >= :points")
    fun deductPoints(userId: Long, points: Int): Int
}
