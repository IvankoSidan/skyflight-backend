package com.wheezy.server.Config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class TelegramBotConfig {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
