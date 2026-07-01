package com.wheezy.server.Models

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reviews")
data class Review(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "booking_id", nullable = false, unique = true)
    val bookingId: Long,

    @Column(name = "flight_id", nullable = false)
    val flightId: Long,

    @Column(name = "airline_name", nullable = false)
    val airlineName: String,

    @Column(name = "rating", nullable = false)
    val rating: Int,

    @Column(name = "comment", length = 500)
    val comment: String? = null,

    @Column(name = "is_hidden")
    var isHidden: Boolean = false,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)