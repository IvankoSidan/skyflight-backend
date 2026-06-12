package com.wheezy.server.Controller

import com.wheezy.server.Models.Notification
import com.wheezy.server.Repository.UserRepository
import com.wheezy.server.Service.NotificationSenderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationSenderService: NotificationSenderService,
    private val userRepository: UserRepository
) {

    @PostMapping
    fun createNotification(
        principal: Principal,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "User not found"))

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "User ID is null"))

        val message = request["message"]?.take(500)
            ?: return ResponseEntity.badRequest()
                .body(mapOf("error" to "Message is required"))

        val notification = Notification(
            userId = userId,
            message = message,
            isRead = false
        )

        notificationSenderService.sendNotification(userId, notification)

        return ResponseEntity.ok(mapOf("message" to "Notification sent"))
    }
}
