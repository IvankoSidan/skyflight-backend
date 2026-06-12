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

@Service
class BrevoApiService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    @Value("\${brevo.api.url}") private val baseUrl: String,
    @Value("\${brevo.from.email}") private val fromEmail: String,
    @Value("\${brevo.from.name}") private val fromName: String,
    @Value("\${brevo.enabled}") private val enabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val jsonMediaType = "application/json".toMediaType()

    data class EmailResult(
        val success: Boolean,
        val messageId: String? = null,
        val error: String? = null
    )

    suspend fun sendTransactionalEmail(
        toEmail: String,
        toName: String? = null,
        subject: String,
        htmlContent: String? = null,
        templateId: Int? = null,
        params: Map<String, Any>? = null,
        attachment: Any? = null
    ): EmailResult {
        if (!enabled) {
            return EmailResult(success = true, messageId = "disabled")
        }

        return try {
            val json = JsonObject().apply {
                add("sender", JsonObject().apply {
                    addProperty("email", fromEmail)
                    addProperty("name", fromName)
                })
                add("to", gson.toJsonTree(listOf(JsonObject().apply {
                    addProperty("email", toEmail)
                    toName?.let { addProperty("name", it) }
                })))
                addProperty("subject", subject)
                htmlContent?.let { addProperty("htmlContent", it) }
                params?.let {
                    val paramsJson = JsonObject()
                    it.forEach { (key, value) -> paramsJson.addProperty(key, value.toString()) }
                    add("params", paramsJson)
                }
            }

            val request = Request.Builder()
                .url("$baseUrl/smtp/email")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                logger.info("Email sent to $toEmail")
                EmailResult(success = true)
            } else {
                logger.error("Failed to send email: ${response.code}")
                EmailResult(success = false, error = "HTTP ${response.code}")
            }
        } catch (e: Exception) {
            logger.error("Email error", e)
            EmailResult(success = false, error = e.message)
        }
    }
}
