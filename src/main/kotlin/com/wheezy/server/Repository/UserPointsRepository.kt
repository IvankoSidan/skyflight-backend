package com.wheezy.server.Repository

import com.wheezy.server.Models.UserPoints
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
interface UserPointsRepository : JpaRepository<UserPoints, Long> {

    fun findByUserId(userId: Long): Optional<UserPoints>

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.balance = up.balance + :points, up.lifetimePoints = up.lifetimePoints + :points WHERE up.userId = :userId")
    fun addPoints(@Param("userId") userId: Long, @Param("points") points: Int)

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.balance = up.balance - :points WHERE up.userId = :userId AND up.balance >= :points")
    fun deductPoints(@Param("userId") userId: Long, @Param("points") points: Int): Int

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.frozenBalance = up.frozenBalance + :points WHERE up.userId = :userId")
    fun freezePoints(@Param("userId") userId: Long, @Param("points") points: Int)

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.frozenBalance = up.frozenBalance - :points WHERE up.userId = :userId AND up.frozenBalance >= :points")
    fun unfreezePoints(@Param("userId") userId: Long, @Param("points") points: Int): Int

    @Modifying
    @Transactional
    @Query("UPDATE UserPoints up SET up.balance = up.balance - :points, up.frozenBalance = up.frozenBalance - :points WHERE up.userId = :userId AND up.frozenBalance >= :points AND up.balance >= :points")
    fun confirmPointsDeduction(@Param("userId") userId: Long, @Param("points") points: Int): Int

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserPoints up 
        SET up.balance = up.balance + :points, 
            up.lifetimePoints = up.lifetimePoints + :points,
            up.tier = (
                SELECT COALESCE(tb.tier, 'BRONZE') 
                FROM TierBenefit tb 
                WHERE tb.minPoints <= up.lifetimePoints + :points 
                ORDER BY tb.minPoints DESC 
                LIMIT 1
            ),
            up.updatedAt = CURRENT_TIMESTAMP
        WHERE up.userId = :userId
    """)
    fun addPointsWithTierUpdate(@Param("userId") userId: Long, @Param("points") points: Int)

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserPoints up 
        SET up.balance = up.balance - :points,
            up.tier = (
                SELECT COALESCE(tb.tier, 'BRONZE') 
                FROM TierBenefit tb 
                WHERE tb.minPoints <= up.lifetimePoints 
                ORDER BY tb.minPoints DESC 
                LIMIT 1
            ),
            up.updatedAt = CURRENT_TIMESTAMP
        WHERE up.userId = :userId AND up.balance >= :points
    """)
    fun deductPointsWithTierUpdate(@Param("userId") userId: Long, @Param("points") points: Int): Int

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserPoints up 
        SET up.balance = up.balance - :points, 
            up.frozenBalance = up.frozenBalance - :points,
            up.tier = (
                SELECT COALESCE(tb.tier, 'BRONZE') 
                FROM TierBenefit tb 
                WHERE tb.minPoints <= up.lifetimePoints 
                ORDER BY tb.minPoints DESC 
                LIMIT 1
            ),
            up.updatedAt = CURRENT_TIMESTAMP
        WHERE up.userId = :userId AND up.frozenBalance >= :points AND up.balance >= :points
    """)
    fun confirmPointsDeductionWithTierUpdate(@Param("userId") userId: Long, @Param("points") points: Int): Int
}