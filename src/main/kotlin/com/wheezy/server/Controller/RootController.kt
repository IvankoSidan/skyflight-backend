package com.wheezy.server.Controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RootController {

    @GetMapping("/")
    fun root(): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(mapOf(
                "message" to "SkyFlight API is running",
                "status" to "healthy",
                "version" to "2.0.0"
            ))
    }
}