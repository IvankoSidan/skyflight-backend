package com.wheezy.server.Config

import com.wheezy.server.Security.AuthChannelInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import org.springframework.web.socket.server.support.DefaultHandshakeHandler

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val authChannelInterceptor: AuthChannelInterceptor
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        val taskScheduler = ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("ws-heartbeat-")
            initialize()
        }

        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(longArrayOf(10000, 10000))
            .setTaskScheduler(taskScheduler)

        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(
                "https://skyflightbooking.ru",
                "https://www.skyflightbooking.ru",
                "http://localhost:8080",
                "http://localhost:3000",
                "http://89.108.81.227:8080"
            )
            .setHandshakeHandler(DefaultHandshakeHandler())
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration
            .setMessageSizeLimit(128 * 1024)
            .setSendTimeLimit(120_000)
            .setSendBufferSizeLimit(512 * 1024)
            .setTimeToFirstMessage(120_000)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(authChannelInterceptor)
    }
}