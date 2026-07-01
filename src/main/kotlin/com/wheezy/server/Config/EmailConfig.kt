package com.wheezy.server.Config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.Properties

@Configuration
class EmailConfig(
    @Value("\${spring.mail.host}") private val host: String,
    @Value("\${spring.mail.port}") private val port: Int,
    @Value("\${spring.mail.username}") private val username: String,
    @Value("\${spring.mail.password}") private val password: String
) {

    @Bean
    fun javaMailSender(): JavaMailSender {
        val mailSender = JavaMailSenderImpl()
        mailSender.host = host
        mailSender.port = port
        mailSender.username = username
        mailSender.password = password

        val props = Properties()
        props["mail.smtp.auth"] = true
        props["mail.smtp.starttls.enable"] = true
        props["mail.smtp.starttls.required"] = true
        props["mail.smtp.connectiontimeout"] = 30000
        props["mail.smtp.timeout"] = 30000
        props["mail.smtp.writetimeout"] = 30000
        props["mail.smtp.ssl.trust"] = host

        mailSender.javaMailProperties = props
        return mailSender
    }
}