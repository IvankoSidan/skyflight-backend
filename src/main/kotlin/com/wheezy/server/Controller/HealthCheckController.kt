package com.wheezy.server.Controller

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory

@RestController
@RequestMapping("/api/health")
class HealthCheckController(
    private val jdbcTemplate: JdbcTemplate
) {

    @GetMapping
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        val status = mutableMapOf<String, Any>()

        status["server"] = "UP"
        status["uptime"] = ManagementFactory.getRuntimeMXBean().uptime / 1000

        try {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            status["database"] = "UP"
        } catch (e: Exception) {
            status["database"] = "DOWN: ${e.message}"
        }

        val isHealthy = status["database"] == "UP"
        status["status"] = if (isHealthy) "HEALTHY" else "UNHEALTHY"

        return if (isHealthy) {
            ResponseEntity.ok(status)
        } else {
            ResponseEntity.status(503).body(status)
        }
    }
}
