package com.wheezy.server.Service

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class BrevoEmailService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    @Value("\${brevo.api.url}") private val baseUrl: String,
    @Value("\${brevo.from.email}") private val fromEmail: String,
    @Value("\${brevo.from.name}") private val fromName: String,
    @Value("\${brevo.enabled}") private val enabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val jsonMediaType = "application/json".toMediaType()

    fun sendEmail(to: String, subject: String, htmlContent: String): Boolean {
        if (!enabled) return true

        return try {
            val json = JsonObject().apply {
                add("sender", JsonObject().apply {
                    addProperty("email", fromEmail)
                    addProperty("name", fromName)
                })
                add("to", gson.toJsonTree(listOf(JsonObject().apply {
                    addProperty("email", to)
                })))
                addProperty("subject", subject)
                addProperty("htmlContent", htmlContent)
            }

            val response = httpClient.newCall(Request.Builder()
                .url("$baseUrl/smtp/email")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()).execute()

            if (response.isSuccessful) {
                logger.info("Email sent to $to")
                true
            } else {
                logger.error("Failed to send email: ${response.code}")
                false
            }
        } catch (e: Exception) {
            logger.error("Email error", e)
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
        if (!enabled) return true

        return try {
            val base64Attachment = Base64.getEncoder().encodeToString(attachmentData)

            val json = JsonObject().apply {
                add("sender", JsonObject().apply {
                    addProperty("email", fromEmail)
                    addProperty("name", fromName)
                })
                add("to", gson.toJsonTree(listOf(JsonObject().apply {
                    addProperty("email", to)
                })))
                addProperty("subject", subject)
                addProperty("htmlContent", htmlContent)
                add("attachment", gson.toJsonTree(listOf(JsonObject().apply {
                    addProperty("name", attachmentName)
                    addProperty("content", base64Attachment)
                })))
            }

            val response = httpClient.newCall(Request.Builder()
                .url("$baseUrl/smtp/email")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()).execute()

            if (response.isSuccessful) {
                logger.info("Email with attachment sent to $to")
                true
            } else {
                logger.error("Failed to send email with attachment: ${response.code}")
                false
            }
        } catch (e: Exception) {
            logger.error("Email with attachment error", e)
            false
        }
    }
}
