package com.wheezy.server.Controller

import com.wheezy.server.DTO.ProfileResponse
import com.wheezy.server.DTO.UpdateProfileRequest
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/users/profile")
class ProfileController(
    private val profileService: ProfileService,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getProfile(principal: Principal): ResponseEntity<ProfileResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val profile = profileService.getProfile(userId)
        return ResponseEntity.ok(profile)
    }

    @PutMapping
    fun updateProfile(
        principal: Principal,
        @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<ProfileResponse> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val updated = profileService.updateProfile(userId, request)
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/picture")
    fun uploadProfilePicture(
        principal: Principal,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val imageBase64 = request["image"]
        if (imageBase64.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Image is required"))
        }

        return try {
            val url = profileService.uploadProfilePicture(userId, imageBase64)
            log.info("✅ Profile picture uploaded for user $userId: $url")
            ResponseEntity.ok(mapOf("url" to url))
        } catch (e: Exception) {
            log.error("❌ Failed to upload profile picture for user $userId", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "Failed to upload picture")))
        }
    }

    @DeleteMapping("/picture")
    fun deleteProfilePicture(principal: Principal): ResponseEntity<Unit> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        profileService.deleteProfilePicture(userId)
        return ResponseEntity.ok().build()
    }
}