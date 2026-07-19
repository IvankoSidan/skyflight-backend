package com.wheezy.server.Service

import com.wheezy.server.DTO.ProfileResponse
import com.wheezy.server.DTO.UpdateProfileRequest
import com.wheezy.server.Models.User
import com.wheezy.server.Repository.BookingRepository
import com.wheezy.server.Repository.UserPointsRepository
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Base64
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val userPointsRepository: UserPointsRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getProfile(userId: Long): ProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        val totalBookings = bookingRepository.findByUserId(userId).size
        val userPoints = userPointsRepository.findByUserId(userId).orElse(null)
        val totalPoints = userPoints?.lifetimePoints ?: 0
        val tier = userPoints?.tier ?: "BRONZE"

        return ProfileResponse.fromUser(user, totalBookings, totalPoints, tier)
    }

    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): ProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        val updatedUser = user.copy(
            name = request.name ?: user.name,
            role = user.role ?: "CUSTOMER"
        )

        userRepository.save(updatedUser)
        log.info("Profile updated for user $userId")

        return getProfile(userId)
    }

    @Transactional
    fun uploadProfilePicture(userId: Long, imageBase64: String): String {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        user.profilePicture?.let { oldPath ->
            try {
                val oldFile = File(oldPath)
                if (oldFile.exists()) {
                    oldFile.delete()
                    log.info("Deleted old profile picture: $oldPath")
                }
            } catch (_: Exception) { }
        }

        val fileName = "profile_${userId}_${UUID.randomUUID()}.jpg"
        val uploadDir = File("uploads/profiles")
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
            log.info("Created upload directory: ${uploadDir.absolutePath}")
        }

        val filePath = uploadDir.absolutePath + File.separator + fileName
        val destFile = File(filePath)

        try {
            val imageBytes = Base64.getDecoder().decode(imageBase64)
            destFile.writeBytes(imageBytes)
            log.info("File saved: ${destFile.absolutePath}, size: ${destFile.length()} bytes")
        } catch (e: Exception) {
            log.error("Failed to save file", e)
            throw RuntimeException("Failed to save image file", e)
        }

        val url = "/uploads/profiles/$fileName"
        val updatedUser = user.copy(
            profilePicture = url,
            role = user.role ?: "CUSTOMER"
        )
        userRepository.save(updatedUser)

        log.info("✅ Profile picture uploaded for user $userId: $url")
        return url
    }

    @Transactional
    fun deleteProfilePicture(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        user.profilePicture?.let { oldPath ->
            try {
                val oldFile = File(oldPath)
                if (oldFile.exists()) {
                    oldFile.delete()
                    log.info("Deleted profile picture: $oldPath")
                }
            } catch (_: Exception) { }
        }

        val updatedUser = user.copy(
            profilePicture = null,
            role = user.role ?: "CUSTOMER"
        )
        userRepository.save(updatedUser)
        log.info("Profile picture deleted for user $userId")
    }
}