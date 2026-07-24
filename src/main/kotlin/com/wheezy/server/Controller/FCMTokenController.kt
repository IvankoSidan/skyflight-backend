package com.wheezy.server.Controller

import com.wheezy.server.Models.UserFCMToken
import com.wheezy.server.Repository.UserFCMTokenRepository
import com.wheezy.server.Repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/fcm")
class FCMTokenController(
    private val userFCMTokenRepository: UserFCMTokenRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/register")
    fun registerToken(
        authentication: Authentication,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.name
        logger.info("FCM register request from user: $email")

        val user = userRepository.findByEmail(email)
        if (user == null) {
            logger.warn("User not found: $email")
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "User not found"))
        }

        val userId = user.id
        if (userId == null) {
            logger.error("User ID is null for user: $email")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "User ID is null"))
        }

        logger.info("Real userId: $userId for email: $email")

        val token = request["token"]
        if (token.isNullOrEmpty()) {
            logger.warn("Token is required")
            return ResponseEntity.badRequest().body(mapOf("error" to "Token is required"))
        }

        logger.info("Registering token for userId: $userId, token: ${token.take(10)}...")

        val existingToken = userFCMTokenRepository.findByToken(token)
        if (existingToken != null) {
            if (existingToken.userId == userId) {
                logger.info("Token already registered for this user")
                return ResponseEntity.ok(mapOf("message" to "Token already registered"))
            }
            logger.info("Token exists for different user, deleting old token")
            userFCMTokenRepository.deleteByToken(token)
        }

        userFCMTokenRepository.deleteByUserId(userId)

        val fcmToken = UserFCMToken(userId = userId, token = token)
        return try {
            userFCMTokenRepository.save(fcmToken)
            logger.info("FCM token saved successfully for userId: $userId")
            ResponseEntity.ok(mapOf("message" to "Token registered successfully"))
        } catch (e: Exception) {
            logger.error("Failed to save FCM token", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to save token"))
        }
    }

    @DeleteMapping("/unregister")
    fun unregisterToken(
        authentication: Authentication,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val email = authentication.name
        logger.info("FCM unregister request from user: $email")

        val user = userRepository.findByEmail(email)
        if (user == null) {
            logger.warn("User not found: $email")
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "User not found"))
        }

        val userId = user.id
        if (userId == null) {
            logger.error("User ID is null for user: $email")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "User ID is null"))
        }

        val token = request["token"]
        if (token.isNullOrEmpty()) {
            logger.warn("Token is required")
            return ResponseEntity.badRequest().body(mapOf("error" to "Token is required"))
        }

        logger.info("Unregistering token for userId: $userId, token: ${token.take(10)}...")
        userFCMTokenRepository.deleteByUserIdAndToken(userId, token)

        logger.info("Token unregistered successfully for userId: $userId")
        return ResponseEntity.ok(mapOf("message" to "Token unregistered successfully"))
    }
}