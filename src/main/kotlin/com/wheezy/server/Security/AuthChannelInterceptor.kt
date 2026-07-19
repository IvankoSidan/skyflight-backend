package com.wheezy.server.Security

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthChannelInterceptor(
    private val jwtUtil: JwtUtil
) : ChannelInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)

        when (accessor.command) {
            StompCommand.CONNECT, StompCommand.STOMP -> {
                var token: String? = null

                val authHeader = accessor.getFirstNativeHeader("Authorization")
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7)
                }

                if (token == null) {
                    token = accessor.getFirstNativeHeader("token")
                }

                if (token == null) {
                    val connectToken = accessor.getFirstNativeHeader("authorization")
                    if (connectToken != null && connectToken.startsWith("Bearer ")) {
                        token = connectToken.substring(7)
                    }
                }

                if (token != null) {
                    try {
                        val username = jwtUtil.extractUsername(token)
                        if (jwtUtil.validateToken(token, username)) {
                            val authentication = UsernamePasswordAuthenticationToken(
                                username, null, emptyList()
                            )
                            accessor.setUser(authentication)
                            SecurityContextHolder.getContext().authentication = authentication
                            logger.debug("WebSocket authenticated for user: $username")
                        } else {
                            logger.warn("Invalid JWT token for WebSocket connection")
                        }
                    } catch (e: Exception) {
                        logger.warn("Error validating JWT token: ${e.message}")
                    }
                } else {
                    logger.warn("WebSocket connection without Authorization header or token")
                }
            }

            StompCommand.DISCONNECT -> {
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication != null && authentication.isAuthenticated) {
                    logger.debug("User disconnected: ${authentication.name}")
                }
                SecurityContextHolder.clearContext()
            }

            else -> {}
        }
        return message
    }

}