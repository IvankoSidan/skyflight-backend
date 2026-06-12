package com.wheezy.server.DTO

import com.wheezy.server.Models.Review
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class CreateReviewRequest(
    val bookingId: Long,
    val rating: Int,
    val comment: String? = null
)

data class UpdateReviewRequest(
    val rating: Int,
    val comment: String? = null
)

data class ReviewResponse(
    val id: Long,
    val userId: Long,
    val userName: String?,
    val bookingId: Long,
    val flightId: Long,
    val airlineName: String,
    val rating: Int,
    val comment: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val canEdit: Boolean = false
)

data class AirlineRatingResponse(
    val airlineName: String,
    val averageRating: Double,
    val totalReviews: Int,
    val ratingDistribution: Map<Int, Int>
)

fun Review.toResponse(userName: String? = null, canEdit: Boolean = false): ReviewResponse {
    return ReviewResponse(
        id = id,
        userId = userId,
        userName = userName,
        bookingId = bookingId,
        flightId = flightId,
        airlineName = airlineName,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        updatedAt = updatedAt,
        canEdit = canEdit
    )
}

fun List<Review>.toAirlineRatingResponse(airlineName: String): AirlineRatingResponse {
    val totalReviews = size
    val averageRating = if (totalReviews > 0) {
        (map { it.rating }.average() * 2).roundToInt() / 2.0
    } else 0.0

    val distribution = groupBy { it.rating }.mapValues { it.value.size }

    return AirlineRatingResponse(
        airlineName = airlineName,
        averageRating = averageRating,
        totalReviews = totalReviews,
        ratingDistribution = (1..5).associateWith { distribution[it] ?: 0 }
    )
}
