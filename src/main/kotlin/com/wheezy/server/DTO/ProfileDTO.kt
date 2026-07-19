package com.wheezy.server.DTO

import com.wheezy.server.Models.User
import java.time.format.DateTimeFormatter

data class ProfileResponse(
    val id: Long,
    val email: String,
    val name: String,
    val profilePicture: String?,
    val memberSince: String?,
    val totalBookings: Int,
    val totalPoints: Int,
    val tier: String
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy, HH:mm", java.util.Locale.US)

        fun fromUser(
            user: User,
            totalBookings: Int = 0,
            totalPoints: Int = 0,
            tier: String = "BRONZE"
        ): ProfileResponse {
            return ProfileResponse(
                id = user.id ?: 0,
                email = user.email,
                name = user.name ?: "",
                profilePicture = user.profilePicture,
                memberSince = user.createdAt.format(formatter),
                totalBookings = totalBookings,
                totalPoints = totalPoints,
                tier = tier
            )
        }
    }
}

data class UpdateProfileRequest(
    val name: String? = null
)