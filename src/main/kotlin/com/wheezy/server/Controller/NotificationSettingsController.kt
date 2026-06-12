package com.wheezy.server.Controller

import com.wheezy.server.DTO.NotificationSettingsDTO
import com.wheezy.server.DTO.toDTO
import com.wheezy.server.Repository.UserNotificationSettingsRepository
import com.wheezy.server.Repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/users/notification-settings")
class NotificationSettingsController(
    private val settingsRepository: UserNotificationSettingsRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getNotificationSettings(principal: Principal): ResponseEntity<NotificationSettingsDTO> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val settings = settingsRepository.findByUserId(userId)

        return if (settings.isPresent) {
            ResponseEntity.ok(settings.get().toDTO())
        } else {
            // Создаём настройки по умолчанию
            val defaultSettings = NotificationSettingsDTO().toEntity(userId)
            val saved = settingsRepository.save(defaultSettings)
            ResponseEntity.ok(saved.toDTO())
        }
    }

    @PutMapping
    fun updateNotificationSettings(
        principal: Principal,
        @RequestBody dto: NotificationSettingsDTO
    ): ResponseEntity<NotificationSettingsDTO> {
        val user = userRepository.findByEmail(principal.name)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val userId = user.id ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()

        val existingSettings = settingsRepository.findByUserId(userId)

        val updatedSettings = if (existingSettings.isPresent) {
            val settings = existingSettings.get()
            settings.bookingCreated = dto.bookingCreated
            settings.bookingConfirmed = dto.bookingConfirmed
            settings.bookingCancelled = dto.bookingCancelled
            settings.paymentSuccess = dto.paymentSuccess
            settings.paymentFailed = dto.paymentFailed
            settings.flightReminder = dto.flightReminder
            settings.flightStatusUpdate = dto.flightStatusUpdate
            settings.massPromotion = dto.massPromotion
            settings.thankYouAfterFlight = dto.thankYouAfterFlight
            settings.quietHoursEnabled = dto.quietHoursEnabled
            settings.quietHoursStart = dto.quietHoursStart
            settings.quietHoursEnd = dto.quietHoursEnd
            settings.pushEnabled = dto.pushEnabled
            settings.emailEnabled = dto.emailEnabled
            settings.updatedAt = java.time.LocalDateTime.now()
            settingsRepository.save(settings)
            settings
        } else {
            val newSettings = dto.toEntity(userId)
            settingsRepository.save(newSettings)
        }

        return ResponseEntity.ok(updatedSettings.toDTO())
    }
}
