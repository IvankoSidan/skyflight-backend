package com.wheezy.server.Сonfig

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class RateLimitingFilter : OncePerRequestFilter() {

    companion object {
        private const val RATE_LIMIT_PREFIX = "rate_limit:"
    }

    private val rateLimiter = ConcurrentHashMap<String, AtomicInteger>()

    @Value("\${rate.limit.max-requests:100}")
    private var maxRequests: Int = 100

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/health") ||
                path.startsWith("/api/public/") ||
                path.startsWith("/api/stripe/webhook") ||
                path.startsWith("/webhook/telegram") ||
                path.startsWith("/actuator/health")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = getClientIp(request)
        val key = "$RATE_LIMIT_PREFIX$clientIp"

        val counter = rateLimiter.computeIfAbsent(key) { AtomicInteger(0) }
        val currentCount = counter.incrementAndGet()

        // Reset after 1 minute
        if (currentCount == 1) {
            Thread {
                Thread.sleep(60000)
                rateLimiter.remove(key)
            }.start()
        }

        response.setHeader("X-RateLimit-Limit", maxRequests.toString())
        response.setHeader("X-RateLimit-Remaining", (maxRequests - currentCount).toString())
        response.setHeader("X-RateLimit-Reset", (System.currentTimeMillis() + 60000).toString())

        if (currentCount > maxRequests) {
            response.status = 429
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write("""{"error": "Too many requests. Please try again later.", "code": 429}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",")[0].trim()
        }
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp
        }
        return request.remoteAddr ?: "unknown"
    }
}
