package com.wheezy.server.Security

import com.wheezy.server.Repository.UserDetailsLoader
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val userDetailsLoader: UserDetailsLoader
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return request.method.equals("OPTIONS", true) ||
                path.startsWith("/login/oauth2") ||
                path.startsWith("/oauth2") ||
                (path.startsWith("/api/auth") && !path.equals("/api/auth/me")) ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/swagger-ui.html") ||
                path.startsWith("/api/stripe/webhook") ||
                path.startsWith("/api/health") ||
                path.startsWith("/ws")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val token = authHeader.removePrefix("Bearer ").trim()
            if (token.isBlank()) {
                filterChain.doFilter(request, response)
                return
            }

            val email = jwtUtil.extractUsername(token)

            if (!email.isNullOrEmpty() && SecurityContextHolder.getContext().authentication == null) {
                val user = userDetailsLoader.findByEmail(email)

                if (user != null && jwtUtil.validateToken(token, email)) {
                    val authToken = UsernamePasswordAuthenticationToken(
                        email, null, emptyList()
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
        } catch (e: Exception) {
            logger.debug("Invalid JWT token: ${e.message}")
        }

        filterChain.doFilter(request, response)
    }
}