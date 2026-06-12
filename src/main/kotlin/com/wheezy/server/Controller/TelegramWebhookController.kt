package com.wheezy.server.Controller

import com.wheezy.server.Service.TelegramMessageHandler
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/webhook")
class TelegramWebhookController(
    private val messageHandler: TelegramMessageHandler
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/telegram")
    fun handleWebhook(@RequestBody update: Map<String, Any>): Map<String, Boolean> {
        logger.debug("Received webhook update")

        val callbackQuery = update["callback_query"] as? Map<String, Any>
        if (callbackQuery != null) {
            messageHandler.handleCallback(callbackQuery)
            return mapOf("ok" to true)
        }

        val message = update["message"] as? Map<String, Any>
        if (message != null) {
            messageHandler.handleMessage(message)
        }

        return mapOf("ok" to true)
    }

    @GetMapping("/telegram")
    fun getWebhookInfo(): String {
        return "✅ SkyFlight Telegram Bot Webhook is active"
    }
}
