package com.wheezy.server.Security

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
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
                val authHeader = accessor.getFirstNativeHeader("Authorization")

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    val token = authHeader.substring(7)
                    try {
                        val username = jwtUtil.extractUsername(token)
                        if (jwtUtil.validateToken(token, username)) {
                            val authentication = UsernamePasswordAuthenticationToken(
                                username, null, emptyList()
                            )
                            accessor.setUser(authentication)
                            logger.info("WebSocket authenticated for user: $username")
                        } else {
                            logger.warn("Invalid JWT token for WebSocket connection")
                        }
                    } catch (e: Exception) {
                        logger.warn("Error validating JWT token: ${e.message}")
                    }
                } else {
                    logger.warn("WebSocket connection without Authorization header")
                }
            }
            else -> {}
        }
        return message
    }
}