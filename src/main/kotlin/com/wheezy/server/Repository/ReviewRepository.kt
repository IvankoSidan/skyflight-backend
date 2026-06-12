package com.wheezy.server.Repository

import com.wheezy.server.Models.Review
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ReviewRepository : JpaRepository<Review, Long> {

    fun findByFlightId(flightId: Long): List<Review>

    fun findByFlightIdAndIsHiddenFalse(flightId: Long): List<Review>

    fun findByFlightIdAndIsHiddenFalse(flightId: Long, pageable: Pageable): Page<Review>

    fun findByUserId(userId: Long): List<Review>

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Review>

    fun findByBookingId(bookingId: Long): Review?

    fun existsByBookingId(bookingId: Long): Boolean

    @Query("SELECT r FROM Review r WHERE r.isHidden = false ORDER BY r.createdAt DESC")
    fun findAllVisible(): List<Review>

    @Query("SELECT r FROM Review r WHERE r.airlineName = :airlineName AND r.isHidden = false")
    fun findByAirlineNameAndIsHiddenFalse(airlineName: String): List<Review>

    @Query("SELECT r FROM Review r WHERE r.airlineName = :airlineName AND r.isHidden = false")
    fun findByAirlineNameAndIsHiddenFalse(airlineName: String, pageable: Pageable): Page<Review>

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.airlineName = :airlineName AND r.isHidden = false")
    fun getAverageRatingByAirline(airlineName: String): Double?

    @Query("SELECT COUNT(r) FROM Review r WHERE r.airlineName = :airlineName AND r.isHidden = false")
    fun getReviewCountByAirline(airlineName: String): Long
}
