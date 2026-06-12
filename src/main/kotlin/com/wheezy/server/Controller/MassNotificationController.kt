package com.wheezy.server.Controller

import com.wheezy.server.DTO.MassNotificationRequest
import com.wheezy.server.DTO.MassNotificationResponse
import com.wheezy.server.Service.MassNotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications/mass")
class MassNotificationController(
    private val massNotificationService: MassNotificationService
) {

    @PostMapping
    fun sendMassNotification(
        @RequestBody request: MassNotificationRequest
    ): ResponseEntity<MassNotificationResponse> {
        val result = massNotificationService.sendMassNotification(request)
        return ResponseEntity.ok(result)
    }
}
