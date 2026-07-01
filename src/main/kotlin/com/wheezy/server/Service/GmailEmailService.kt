package com.wheezy.server.Service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class GmailEmailService(
    private val gmailApiService: GmailApiService,
    @Value("\${email.enabled}") private val enabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendEmail(to: String, subject: String, htmlContent: String): Boolean {
        if (!enabled || to.isBlank()) {
            return true
        }

        return try {
            gmailApiService.sendEmail(to, subject, htmlContent)
        } catch (e: Exception) {
            logger.error("Failed to send email: ${e.message}", e)
            false
        }
    }

    fun sendEmailWithAttachment(
        to: String,
        subject: String,
        htmlContent: String,
        attachmentName: String,
        attachmentData: ByteArray
    ): Boolean {
        if (!enabled || to.isBlank()) {
            return true
        }

        return try {
            gmailApiService.sendEmailWithAttachment(to, subject, htmlContent, attachmentName, attachmentData)
        } catch (e: Exception) {
            logger.error("Failed to send email with attachment: ${e.message}", e)
            false
        }
    }
}