package com.wheezy.server.Controller

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import javax.sql.DataSource

@RestController
@RequestMapping("/api/health")
class HealthCheckController(
    private val jdbcTemplate: JdbcTemplate,
    private val dataSource: DataSource,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @GetMapping
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        val status = mutableMapOf<String, Any>()
        val details = mutableMapOf<String, Any>()

        status["server"] = "UP"
        status["uptime"] = ManagementFactory.getRuntimeMXBean().uptime / 1000

        try {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            details["database"] = "UP"
        } catch (e: Exception) {
            details["database"] = "DOWN: ${e.message}"
        }

        try {
            val ping = redisTemplate.connectionFactory?.connection?.ping()
            details["redis"] = if (ping != null) "UP" else "DOWN"
        } catch (e: Exception) {
            details["redis"] = "DOWN: ${e.message}"
        }

        try {
            dataSource.connection.use { conn ->
                details["connection_pool"] = conn.isValid(1)
            }
        } catch (e: Exception) {
            details["connection_pool"] = "DOWN: ${e.message}"
        }

        status["details"] = details
        status["status"] = if (details["database"] == "UP") "HEALTHY" else "UNHEALTHY"

        return if (status["status"] == "HEALTHY") {
            ResponseEntity.ok(status)
        } else {
            ResponseEntity.status(503).body(status)
        }
    }
}