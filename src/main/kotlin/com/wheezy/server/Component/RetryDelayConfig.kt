package com.wheezy.server.Component

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "retry.delay")
class RetryDelayConfig {
    var delay1: Long = 5
    var delay2: Long = 15
    var defaultDelay: Long = 30

    fun getDelay(retryCount: Int): Long = when (retryCount) {
        1 -> delay1
        2 -> delay2
        else -> defaultDelay
    }
}
