package com.wheezy.server.DTO

import com.wheezy.server.Models.UserNotificationSettings

data class NotificationSettingsDTO(
    val bookingCreated: Boolean = true,
    val bookingConfirmed: Boolean = true,
    val bookingCancelled: Boolean = true,
    val paymentSuccess: Boolean = true,
    val paymentFailed: Boolean = true,
    val flightReminder: Boolean = true,
    val flightStatusUpdate: Boolean = true,
    val massPromotion: Boolean = false,
    val thankYouAfterFlight: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 23,
    val quietHoursEnd: Int = 8,
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = true
) {
    fun toEntity(userId: Long): UserNotificationSettings {
        return UserNotificationSettings(
            userId = userId,
            bookingCreated = this.bookingCreated,
            bookingConfirmed = this.bookingConfirmed,
            bookingCancelled = this.bookingCancelled,
            paymentSuccess = this.paymentSuccess,
            paymentFailed = this.paymentFailed,
            flightReminder = this.flightReminder,
            flightStatusUpdate = this.flightStatusUpdate,
            massPromotion = this.massPromotion,
            thankYouAfterFlight = this.thankYouAfterFlight,
            quietHoursEnabled = this.quietHoursEnabled,
            quietHoursStart = this.quietHoursStart,
            quietHoursEnd = this.quietHoursEnd,
            pushEnabled = this.pushEnabled,
            emailEnabled = this.emailEnabled
        )
    }
}

fun UserNotificationSettings.toDTO(): NotificationSettingsDTO {
    return NotificationSettingsDTO(
        bookingCreated = this.bookingCreated,
        bookingConfirmed = this.bookingConfirmed,
        bookingCancelled = this.bookingCancelled,
        paymentSuccess = this.paymentSuccess,
        paymentFailed = this.paymentFailed,
        flightReminder = this.flightReminder,
        flightStatusUpdate = this.flightStatusUpdate,
        massPromotion = this.massPromotion,
        thankYouAfterFlight = this.thankYouAfterFlight,
        quietHoursEnabled = this.quietHoursEnabled,
        quietHoursStart = this.quietHoursStart,
        quietHoursEnd = this.quietHoursEnd,
        pushEnabled = this.pushEnabled,
        emailEnabled = this.emailEnabled
    )
}
