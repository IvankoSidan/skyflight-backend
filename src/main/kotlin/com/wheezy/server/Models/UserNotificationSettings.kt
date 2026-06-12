package com.wheezy.server.Models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_notification_settings")
data class UserNotificationSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "booking_created")
    var bookingCreated: Boolean = true,

    @Column(name = "booking_confirmed")
    var bookingConfirmed: Boolean = true,

    @Column(name = "booking_cancelled")
    var bookingCancelled: Boolean = true,

    @Column(name = "payment_success")
    var paymentSuccess: Boolean = true,

    @Column(name = "payment_failed")
    var paymentFailed: Boolean = true,

    @Column(name = "flight_reminder")
    var flightReminder: Boolean = true,

    @Column(name = "flight_status_update")
    var flightStatusUpdate: Boolean = true,

    @Column(name = "mass_promotion")
    var massPromotion: Boolean = false,

    @Column(name = "thank_you_after_flight")
    var thankYouAfterFlight: Boolean = true,

    @Column(name = "quiet_hours_enabled")
    var quietHoursEnabled: Boolean = false,

    @Column(name = "quiet_hours_start")
    var quietHoursStart: Int = 23,

    @Column(name = "quiet_hours_end")
    var quietHoursEnd: Int = 8,

    @Column(name = "push_enabled")
    var pushEnabled: Boolean = true,

    @Column(name = "email_enabled")
    var emailEnabled: Boolean = true,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isQuietHour(currentHour: Int = LocalDateTime.now().hour): Boolean {
        if (!quietHoursEnabled) return false
        return if (quietHoursStart > quietHoursEnd) {
            currentHour >= quietHoursStart || currentHour < quietHoursEnd
        } else {
            currentHour in quietHoursStart until quietHoursEnd
        }
    }

    fun shouldSendNotification(notificationType: String): Boolean {
        if (!pushEnabled) return false
        if (isQuietHour()) return false

        return when (notificationType) {
            "booking_created" -> bookingCreated
            "booking_confirmed" -> bookingConfirmed
            "booking_cancelled" -> bookingCancelled
            "payment_success" -> paymentSuccess
            "payment_failed" -> paymentFailed
            "reminder" -> flightReminder
            "flight_status_update" -> flightStatusUpdate
            "mass_promotion" -> massPromotion
            "thank_you" -> thankYouAfterFlight
            else -> true
        }
    }
}
