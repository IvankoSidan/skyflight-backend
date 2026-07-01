package com.wheezy.server.Config

import com.wheezy.server.Service.RateLimitServiceSimple
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RateLimitingFilter(
    private val rateLimitService: RateLimitServiceSimple
) : OncePerRequestFilter() {

    @Value("\${rate.limit.enabled:true}")
    private var rateLimitEnabled: Boolean = true

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !rateLimitEnabled ||
                path.startsWith("/api/health") ||
                path.startsWith("/actuator/health") ||
                path.startsWith("/api/stripe/webhook") ||
                path.startsWith("/webhook/telegram") ||
                path.startsWith("/ws") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-ui")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = getClientIp(request)
        val path = request.requestURI
        val limitType = getLimitType(path)

        val result = rateLimitService.tryConsume(clientIp, limitType)

        // Исправлено: безопасное получение лимита
        val limitConfig = rateLimitService.getLimitConfig(limitType)
        val limitValue = limitConfig?.limit?.toString() ?: "100"

        response.setHeader("X-RateLimit-Limit", limitValue)
        response.setHeader("X-RateLimit-Remaining", result.remainingTokens.toString())
        response.setHeader("X-RateLimit-Reset", result.resetInSeconds.toString())

        if (!result.allowed) {
            response.status = 429
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write("""{"error": "Too many requests. Please try again later.", "code": 429}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun getLimitType(path: String): String {
        return when {
            path.contains("/auth") -> "auth"
            path.contains("/bookings") -> "booking"
            path.contains("/payments") -> "payment"
            path.contains("/search") || path.contains("/flights") -> "search"
            else -> "default"
        }
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