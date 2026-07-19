package com.wheezy.server.Service

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class GmailApiService(
    @Value("\${gmail.api.client-id}") private val clientId: String,
    @Value("\${gmail.api.client-secret}") private val clientSecret: String,
    @Value("\${gmail.api.refresh-token}") private val refreshToken: String,
    @Value("\${gmail.api.user-email}") private val userEmail: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = OkHttpClient()
    private val objectMapper = ObjectMapper()

    private fun getAccessToken(): String? {
        return try {
            val body = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("client_secret", clientSecret.trim())
                .add("refresh_token", refreshToken.trim())
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val jsonNode = objectMapper.readTree(json)
                    jsonNode.get("access_token")?.asText()
                } else {
                    logger.error("Failed to get access token: ${response.code} ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting access token", e)
            null
        }
    }

    fun sendEmail(to: String, subject: String, htmlContent: String): Boolean {
        val recipient = to.trim()
        if (recipient.isBlank()) {
            logger.warn("Recipient email is empty")
            return false
        }

        if (!isValidEmail(recipient)) {
            logger.warn("Invalid email address: $recipient")
            return false
        }

        val accessToken = getAccessToken() ?: run {
            logger.error("Failed to get access token")
            return false
        }

        val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
        if (cleanToken.isEmpty()) {
            logger.error("Access token is empty")
            return false
        }

        val fromEmail = userEmail.trim()
        if (!isValidEmail(fromEmail)) {
            logger.error("Invalid sender email: $fromEmail")
            return false
        }

        return try {
            logger.info("📧 Sending email to: $recipient, subject: $subject")

            val email = buildString {
                append("From: $fromEmail\n")
                append("To: $recipient\n")
                append("Subject: $subject\n")
                append("MIME-Version: 1.0\n")
                append("Content-Type: text/html; charset=utf-8\n")
                append("\n")
                append(htmlContent)
            }

            val encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(email.toByteArray())

            val body = objectMapper.createObjectNode().put("raw", encodedEmail)
            val jsonBody = objectMapper.writeValueAsString(body)

            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/$fromEmail/messages/send")
                .addHeader("Authorization", "Bearer $cleanToken")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    logger.info("✅ Email sent successfully to $recipient")
                } else {
                    val errorBody = response.body?.string()
                    logger.error("❌ Failed to send email: ${response.code} ${response.message} - $errorBody")
                }
                success
            }
        } catch (e: Exception) {
            logger.error("Failed to send email to $recipient", e)
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
        val recipient = to.trim()
        if (recipient.isBlank()) {
            logger.warn("Recipient email is empty")
            return false
        }

        if (!isValidEmail(recipient)) {
            logger.warn("Invalid email address: $recipient")
            return false
        }

        val accessToken = getAccessToken() ?: run {
            logger.error("Failed to get access token")
            return false
        }

        val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
        if (cleanToken.isEmpty()) {
            logger.error("Access token is empty")
            return false
        }

        val fromEmail = userEmail.trim()
        if (!isValidEmail(fromEmail)) {
            logger.error("Invalid sender email: $fromEmail")
            return false
        }

        return try {
            logger.info("📧 Sending email with attachment to: $recipient, subject: $subject")
            logger.info("📎 Attachment: $attachmentName, size: ${attachmentData.size} bytes")

            val boundary = "----=_Part_${System.currentTimeMillis()}"
            val encodedAttachment = Base64.getEncoder().encodeToString(attachmentData)

            val email = buildString {
                append("From: $fromEmail\n")
                append("To: $recipient\n")
                append("Subject: $subject\n")
                append("MIME-Version: 1.0\n")
                append("Content-Type: multipart/mixed; boundary=\"$boundary\"\n")
                append("\n")
                append("--$boundary\n")
                append("Content-Type: text/html; charset=utf-8\n")
                append("\n")
                append(htmlContent)
                append("\n\n")
                append("--$boundary\n")
                append("Content-Type: application/pdf; name=\"$attachmentName\"\n")
                append("Content-Disposition: attachment; filename=\"$attachmentName\"\n")
                append("Content-Transfer-Encoding: base64\n")
                append("\n")
                append(encodedAttachment)
                append("\n\n")
                append("--$boundary--\n")
            }

            val encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(email.toByteArray())

            val body = objectMapper.createObjectNode().put("raw", encodedEmail)
            val jsonBody = objectMapper.writeValueAsString(body)

            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/$fromEmail/messages/send")
                .addHeader("Authorization", "Bearer $cleanToken")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    logger.info("✅ Email with attachment sent successfully to $recipient")
                } else {
                    val errorBody = response.body?.string()
                    logger.error("❌ Failed to send email: ${response.code} ${response.message} - $errorBody")
                }
                success
            }
        } catch (e: Exception) {
            logger.error("Failed to send email with attachment to $recipient", e)
            false
        }
    }

    private fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }
}