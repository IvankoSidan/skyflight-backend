package com.wheezy.server.Controller

import com.wheezy.server.DTO.*
import com.wheezy.server.Models.Review
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.FlightRepository
import com.wheezy.server.Repository.ReviewRepository
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/")
    fun root(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Reviews API is working"))
    }

    @PostMapping
    fun createReview(
        principal: Principal,
        @RequestBody request: CreateReviewRequest
    ): ResponseEntity<ReviewResponse> {
        log.info("📝 CREATE REVIEW: bookingId=${request.bookingId}, rating=${request.rating}")

        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val booking = bookingRepository.findById(request.bookingId).orElse(null)
        if (booking == null || booking.userId != userId) {
            log.warn("❌ Booking not found or not owned: ${request.bookingId}")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val flight = flightRepository.findById(booking.flightId).orElse(null)
        if (flight == null) {
            log.warn("❌ Flight not found: ${booking.flightId}")
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        val canReviewDate = flight.flightDate.minusDays(1)
        if (canReviewDate.isAfter(LocalDate.now())) {
            log.warn("❌ Flight not departed yet: ${flight.flightDate}")
            return ResponseEntity.badRequest().build()
        }

        if (reviewRepository.existsByBookingId(request.bookingId)) {
            log.warn("❌ Review already exists for booking: ${request.bookingId}")
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        if (request.rating !in 1..5) {
            log.warn("❌ Invalid rating: ${request.rating}")
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
        log.info("✅ Review created: id=${saved.id}, flightId=${saved.flightId}")

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(saved.toResponse(user.name, canEdit = true))
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
        if (booking == null || booking.userId != userId) {
            return ResponseEntity.ok(mapOf("canReview" to false))
        }

        val flight = flightRepository.findById(booking.flightId).orElse(null)
        if (flight == null) {
            return ResponseEntity.ok(mapOf("canReview" to false))
        }

        val canReviewDate = flight.flightDate.minusDays(1)
        val flightPassed = !canReviewDate.isAfter(LocalDate.now())
        val hasReview = reviewRepository.existsByBookingId(bookingId)

        log.info("📝 canReview: bookingId=$bookingId, flightDate=${flight.flightDate}, flightPassed=$flightPassed, hasReview=$hasReview")

        return ResponseEntity.ok(mapOf("canReview" to (flightPassed && !hasReview)))
    }

    @GetMapping("/flight/{flightId}")
    fun getFlightReviews(@PathVariable flightId: Long): ResponseEntity<List<ReviewResponse>> {
        log.info("📝 GET FLIGHT REVIEWS: flightId=$flightId")

        val reviews = reviewRepository.findByFlightIdAndIsHiddenFalse(flightId)

        log.info("✅ Found ${reviews.size} reviews for flight $flightId")
        reviews.forEach { review ->
            log.info("   Review: id=${review.id}, rating=${review.rating}, userId=${review.userId}")
        }

        return ResponseEntity.ok(reviews.map { review ->
            val user = userRepository.findById(review.userId).orElse(null)
            review.toResponse(user?.name, canEdit = false)
        })
    }

    @GetMapping("/flight/{flightId}/paginated")
    fun getFlightReviewsPaginated(
        @PathVariable flightId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        log.info("📝 GET FLIGHT REVIEWS PAGINATED: flightId=$flightId, page=$page, size=$size")

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val reviewsPage = reviewRepository.findByFlightIdAndIsHiddenFalse(flightId, pageable)

        log.info("✅ Found ${reviewsPage.content.size} reviews, total=${reviewsPage.totalElements}")

        val response = mapOf(
            "reviews" to reviewsPage.content.map { review ->
                val user = userRepository.findById(review.userId).orElse(null)
                review.toResponse(user?.name, canEdit = false)
            },
            "currentPage" to reviewsPage.number,
            "totalPages" to reviewsPage.totalPages,
            "totalItems" to reviewsPage.totalElements
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/airline/{airlineName}")
    fun getAirlineRating(@PathVariable airlineName: String): ResponseEntity<AirlineRatingResponse> {
        log.info("📝 GET AIRLINE RATING: airlineName=$airlineName")

        val reviews = reviewRepository.findByAirlineNameAndIsHiddenFalse(airlineName)

        log.info("✅ Found ${reviews.size} reviews for airline $airlineName")
        return ResponseEntity.ok(reviews.toAirlineRatingResponse(airlineName))
    }

    @GetMapping("/airline/{airlineName}/paginated")
    fun getAirlineRatingPaginated(
        @PathVariable airlineName: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        log.info("📝 GET AIRLINE REVIEWS PAGINATED: airlineName=$airlineName, page=$page, size=$size")

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val reviewsPage = reviewRepository.findByAirlineNameAndIsHiddenFalse(airlineName, pageable)

        log.info("✅ Found ${reviewsPage.content.size} reviews, total=${reviewsPage.totalElements}")

        val response = mapOf(
            "reviews" to reviewsPage.content.map { review ->
                val user = userRepository.findById(review.userId).orElse(null)
                review.toResponse(user?.name, canEdit = false)
            },
            "currentPage" to reviewsPage.number,
            "totalPages" to reviewsPage.totalPages,
            "totalItems" to reviewsPage.totalElements,
            "airlineRating" to reviewsPage.content.toAirlineRatingResponse(airlineName)
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/my")
    fun getMyReviews(principal: Principal): ResponseEntity<List<ReviewResponse>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        log.info("📝 GET MY REVIEWS: userId=$userId")

        val reviews = reviewRepository.findByUserId(userId)
            .map { review ->
                val canEdit = review.createdAt.isAfter(LocalDateTime.now().minusHours(48))
                review.toResponse(user.name, canEdit = canEdit)
            }

        log.info("✅ Found ${reviews.size} reviews for user $userId")
        return ResponseEntity.ok(reviews)
    }

    @PutMapping("/{id}")
    fun updateReview(
        principal: Principal,
        @PathVariable id: Long,
        @RequestBody request: UpdateReviewRequest
    ): ResponseEntity<ReviewResponse> {
        log.info("📝 UPDATE REVIEW: id=$id, rating=${request.rating}")

        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val review = reviewRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (review.userId != userId) {
            log.warn("❌ Review $id belongs to different user")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (review.createdAt.isBefore(LocalDateTime.now().minusHours(48))) {
            log.warn("❌ Review $id is too old to edit")
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
        log.info("✅ Review updated: id=${saved.id}")

        return ResponseEntity.ok(saved.toResponse(user.name, canEdit = true))
    }

    @DeleteMapping("/{id}")
    fun deleteReview(
        principal: Principal,
        @PathVariable id: Long
    ): ResponseEntity<Void> {
        log.info("📝 DELETE REVIEW: id=$id")

        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val review = reviewRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (review.userId != userId) {
            log.warn("❌ Review $id belongs to different user")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        reviewRepository.deleteById(id)
        log.info("✅ Review deleted: id=$id")

        return ResponseEntity.noContent().build()
    }
}