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
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun sendEmail(to: String, subject: String, htmlContent: String): Boolean {
        val recipient = to.trim()
        if (recipient.isBlank()) {
            return false
        }

        val accessToken = getAccessToken()
        if (accessToken == null) {
            return false
        }

        val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
        if (cleanToken.isEmpty()) {
            return false
        }

        return try {
            val email = "From: $userEmail\n" +
                    "To: $recipient\n" +
                    "Subject: $subject\n" +
                    "MIME-Version: 1.0\n" +
                    "Content-Type: text/html; charset=utf-8\n" +
                    "\n" +
                    htmlContent

            val encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(email.toByteArray())

            val body = objectMapper.createObjectNode().put("raw", encodedEmail)
            val jsonBody = objectMapper.writeValueAsString(body)

            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/$userEmail/messages/send")
                .addHeader("Authorization", "Bearer $cleanToken")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
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
            return false
        }

        val accessToken = getAccessToken()
        if (accessToken == null) {
            return false
        }

        val cleanToken = accessToken.trim().replace("\n", "").replace("\r", "")
        if (cleanToken.isEmpty()) {
            return false
        }

        return try {
            val boundary = "----=_Part_${System.currentTimeMillis()}"
            val encodedAttachment = Base64.getEncoder().encodeToString(attachmentData)

            val email = "From: $userEmail\n" +
                    "To: $recipient\n" +
                    "Subject: $subject\n" +
                    "MIME-Version: 1.0\n" +
                    "Content-Type: multipart/mixed; boundary=\"$boundary\"\n" +
                    "\n" +
                    "--$boundary\n" +
                    "Content-Type: text/html; charset=utf-8\n" +
                    "\n" +
                    htmlContent + "\n\n" +
                    "--$boundary\n" +
                    "Content-Type: application/pdf; name=\"$attachmentName\"\n" +
                    "Content-Disposition: attachment; filename=\"$attachmentName\"\n" +
                    "Content-Transfer-Encoding: base64\n" +
                    "\n" +
                    encodedAttachment + "\n\n" +
                    "--$boundary--\n"

            val encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(email.toByteArray())

            val body = objectMapper.createObjectNode().put("raw", encodedEmail)
            val jsonBody = objectMapper.writeValueAsString(body)

            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/$userEmail/messages/send")
                .addHeader("Authorization", "Bearer $cleanToken")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}