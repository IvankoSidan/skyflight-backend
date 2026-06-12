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

        if (request.method == "DELETE") {
            logger.info("DELETE request to: ${request.requestURI}")
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ").trim()
            val email = jwtUtil.extractUsername(token)

            if (request.method == "DELETE") {
                logger.info("Extracted email: $email")
            }

            if (!email.isNullOrEmpty() && SecurityContextHolder.getContext().authentication == null) {
                val user = userDetailsLoader.findByEmail(email)

                if (user != null && jwtUtil.validateToken(token, email)) {
                    val authToken = UsernamePasswordAuthenticationToken(
                        email, null, emptyList()
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                    if (request.method == "DELETE") {
                        logger.info("✅ Authentication set for DELETE")
                    }
                } else {
                    if (request.method == "DELETE") {
                        logger.warn("❌ Authentication failed: user=${user?.email}, tokenValid=${jwtUtil.validateToken(token, email)}")
                    }
                }
            }
        } else if (request.method == "DELETE") {
            logger.warn("❌ No Authorization header for DELETE")
        }

        filterChain.doFilter(request, response)
    }
}
