package com.wheezy.server.Controller

import com.wheezy.server.Models.UserFCMToken
import com.wheezy.server.Repository.UserFCMTokenRepository
import com.wheezy.server.Repository.UserRepository
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

    @PostMapping("/register")
    fun registerToken(
        authentication: Authentication,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(authentication.name)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "User not found"))

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "User ID is null"))

        val token = request["token"]
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Token is required"))

        userFCMTokenRepository.findByToken(token)?.let {
            if (it.userId == userId) {
                return ResponseEntity.ok(mapOf("message" to "Token already registered"))
            }
            userFCMTokenRepository.deleteByToken(token)
        }

        val fcmToken = UserFCMToken(
            userId = userId,
            token = token
        )
        userFCMTokenRepository.save(fcmToken)

        return ResponseEntity.ok(mapOf("message" to "Token registered successfully"))
    }

    @DeleteMapping("/unregister")
    fun unregisterToken(
        authentication: Authentication,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(authentication.name)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "User not found"))

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "User ID is null"))

        val token = request["token"]
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Token is required"))

        userFCMTokenRepository.deleteByUserIdAndToken(userId, token)

        return ResponseEntity.ok(mapOf("message" to "Token unregistered successfully"))
    }
}
