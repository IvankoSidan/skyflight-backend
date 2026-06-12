package com.wheezy.server.Controller

import com.wheezy.server.DTO.*
import com.wheezy.server.Models.Review
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.ReviewRepository
import com.wheezy.server.Repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/reviews")
class ReviewController(
    private val reviewRepository: ReviewRepository,
    private val bookingRepository: BookingRepository,
    private val flightRepository: FlightRepository,
    private val userRepository: UserRepository
) {

    @PostMapping
    fun createReview(
        principal: Principal,
        @RequestBody request: CreateReviewRequest
    ): ResponseEntity<ReviewResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val booking = bookingRepository.findById(request.bookingId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (booking.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val flight = flightRepository.findById(booking.flightId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (flight.flightDate.isAfter(LocalDate.now())) {
            return ResponseEntity.badRequest().build()
        }

        if (reviewRepository.existsByBookingId(request.bookingId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        if (request.rating !in 1..5) {
            return ResponseEntity.badRequest().build()
        }

        val review = Review(
            userId = userId,
            bookingId = request.bookingId,
            flightId = booking.flightId,
            airlineName = flight.airlineName,
            rating = request.rating,
            comment = request.comment?.take(500)
        )

        val saved = reviewRepository.save(review)

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(saved.toResponse(user.name, canEdit = true))
    }

    @PutMapping("/{id}")
    fun updateReview(
        principal: Principal,
        @PathVariable id: Long,
        @RequestBody request: UpdateReviewRequest
    ): ResponseEntity<ReviewResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val review = reviewRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (review.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (review.createdAt.isBefore(LocalDateTime.now().minusHours(24))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updatedReview = Review(
            id = review.id,
            userId = review.userId,
            bookingId = review.bookingId,
            flightId = review.flightId,
            airlineName = review.airlineName,
            rating = request.rating,
            comment = request.comment?.take(500),
            isHidden = review.isHidden,
            createdAt = review.createdAt,
            updatedAt = LocalDateTime.now()
        )

        val saved = reviewRepository.save(updatedReview)

        return ResponseEntity.ok(saved.toResponse(user.name, canEdit = true))
    }

    @DeleteMapping("/{id}")
    fun deleteReview(
        principal: Principal,
        @PathVariable id: Long
    ): ResponseEntity<Void> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val review = reviewRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (review.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        reviewRepository.deleteById(id)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/flight/{flightId}")
    fun getFlightReviews(@PathVariable flightId: Long): ResponseEntity<List<ReviewResponse>> {
        val reviews = reviewRepository.findByFlightIdAndIsHiddenFalse(flightId)
            .map { review ->
                val user = userRepository.findById(review.userId).orElse(null)
                review.toResponse(user?.name, canEdit = false)
            }

        return ResponseEntity.ok(reviews)
    }

    @GetMapping("/flight/{flightId}/paginated")
    fun getFlightReviewsPaginated(
        @PathVariable flightId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val reviewsPage = reviewRepository.findByFlightIdAndIsHiddenFalse(flightId, pageable)

        return ResponseEntity.ok(mapOf(
            "reviews" to reviewsPage.content.map { review ->
                val user = userRepository.findById(review.userId).orElse(null)
                review.toResponse(user?.name, canEdit = false)
            },
            "currentPage" to reviewsPage.number,
            "totalPages" to reviewsPage.totalPages,
            "totalItems" to reviewsPage.totalElements
        ))
    }

    @GetMapping("/airline/{airlineName}")
    fun getAirlineRating(@PathVariable airlineName: String): ResponseEntity<AirlineRatingResponse> {
        val reviews = reviewRepository.findByAirlineNameAndIsHiddenFalse(airlineName)
        return ResponseEntity.ok(reviews.toAirlineRatingResponse(airlineName))
    }

    @GetMapping("/airline/{airlineName}/paginated")
    fun getAirlineRatingPaginated(
        @PathVariable airlineName: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val reviewsPage = reviewRepository.findByAirlineNameAndIsHiddenFalse(airlineName, pageable)

        return ResponseEntity.ok(mapOf(
            "reviews" to reviewsPage.content.map { review ->
                val user = userRepository.findById(review.userId).orElse(null)
                review.toResponse(user?.name, canEdit = false)
            },
            "currentPage" to reviewsPage.number,
            "totalPages" to reviewsPage.totalPages,
            "totalItems" to reviewsPage.totalElements,
            "airlineRating" to reviewsPage.content.toAirlineRatingResponse(airlineName)
        ))
    }

    @GetMapping("/my")
    fun getMyReviews(principal: Principal): ResponseEntity<List<ReviewResponse>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val reviews = reviewRepository.findByUserId(userId)
            .map { review ->
                review.toResponse(user.name, canEdit = review.createdAt.isAfter(LocalDateTime.now().minusHours(24)))
            }

        return ResponseEntity.ok(reviews)
    }

    @GetMapping("/can-review/{bookingId}")
    fun canReview(
        principal: Principal,
        @PathVariable bookingId: Long
    ): ResponseEntity<Map<String, Boolean>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val booking = bookingRepository.findById(bookingId).orElse(null)
            ?: return ResponseEntity.ok(mapOf("canReview" to false))

        if (booking.userId != userId) {
            return ResponseEntity.ok(mapOf("canReview" to false))
        }

        val flight = flightRepository.findById(booking.flightId).orElse(null)
            ?: return ResponseEntity.ok(mapOf("canReview" to false))

        val hasReview = reviewRepository.existsByBookingId(bookingId)
        val flightPassed = flight.flightDate.isBefore(LocalDate.now())

        return ResponseEntity.ok(mapOf("canReview" to (flightPassed && !hasReview)))
    }
}
