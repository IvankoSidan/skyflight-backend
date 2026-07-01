package com.wheezy.server.Service

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class RateLimitServiceSimple(
    private val redisTemplate: RedisTemplate<String, String>
) {

    companion object {
        private const val KEY_PREFIX = "rate_limit:"

        // Исправлено: используем data class вместо Pair
        data class LimitConfig(
            val limit: Int,
            val duration: Duration
        )

        val LIMITS = mapOf(
            "default" to LimitConfig(100, Duration.ofMinutes(1)),
            "auth" to LimitConfig(10, Duration.ofMinutes(1)),
            "booking" to LimitConfig(20, Duration.ofMinutes(1)),
            "payment" to LimitConfig(15, Duration.ofMinutes(1)),
            "search" to LimitConfig(50, Duration.ofMinutes(1))
        )
    }

    fun tryConsume(key: String, limitType: String = "default"): RateLimitResult {
        val redisKey = "$KEY_PREFIX$limitType:$key"
        val config = LIMITS[limitType] ?: LIMITS["default"]!!

        val limit = config.limit
        val duration = config.duration

        val current = redisTemplate.opsForValue().increment(redisKey, 1) ?: 1

        if (current == 1L) {
            redisTemplate.expire(redisKey, duration)
        }

        val allowed = current <= limit
        val remaining = (limit - current).coerceAtLeast(0)

        return RateLimitResult(
            allowed = allowed,
            remainingTokens = remaining,
            limitType = limitType,
            resetInSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)
        )
    }

    fun getLimitConfig(limitType: String): LimitConfig? {
        return LIMITS[limitType] ?: LIMITS["default"]
    }

    data class RateLimitResult(
        val allowed: Boolean,
        val remainingTokens: Long,
        val limitType: String,
        val resetInSeconds: Long = 0,
        val error: String? = null
    )
}