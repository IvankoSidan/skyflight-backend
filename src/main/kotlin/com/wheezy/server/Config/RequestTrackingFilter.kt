package com.wheezy.server.Config

import com.wheezy.server.Component.GracefulShutdown
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RequestTrackingFilter(
    private val gracefulShutdown: GracefulShutdown
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        gracefulShutdown.registerActiveRequest()
        try {
            filterChain.doFilter(request, response)
        } finally {
            gracefulShutdown.unregisterActiveRequest()
        }
    }
}