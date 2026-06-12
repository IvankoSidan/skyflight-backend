package com.wheezy.server.Service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class TelegramBotService(
    private val restTemplate: RestTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${telegram.bot.token}")
    private lateinit var botToken: String

    private val apiUrl: String
        get() = "https://api.telegram.org/bot$botToken"

    fun sendMessage(chatId: String, text: String, replyMarkup: Map<String, Any>? = null) {
        val url = "$apiUrl/sendMessage"
        val body = mutableMapOf<String, Any>(
            "chat_id" to chatId,
            "text" to text,
            "parse_mode" to "HTML",
            "disable_web_page_preview" to true
        )
        replyMarkup?.let { body["reply_markup"] = it }

        try {
            val response = restTemplate.postForObject(url, body, String::class.java)
            logger.info("Message sent to $chatId")
        } catch (e: Exception) {
            logger.error("Error sending message: ${e.message}")
        }
    }

    fun editMessage(chatId: String, messageId: Int, text: String, replyMarkup: Map<String, Any>? = null) {
        val url = "$apiUrl/editMessageText"
        val body = mutableMapOf<String, Any>(
            "chat_id" to chatId,
            "message_id" to messageId,
            "text" to text,
            "parse_mode" to "HTML",
            "disable_web_page_preview" to true
        )
        replyMarkup?.let { body["reply_markup"] = it }

        try {
            restTemplate.postForObject(url, body, String::class.java)
        } catch (e: Exception) {
            logger.error("Error editing message: ${e.message}")
        }
    }

    fun deleteMessage(chatId: String, messageId: Int) {
        val url = "$apiUrl/deleteMessage"
        try {
            restTemplate.postForObject(url, mapOf("chat_id" to chatId, "message_id" to messageId), String::class.java)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun sendChatAction(chatId: String, action: String = "typing") {
        val url = "$apiUrl/sendChatAction"
        try {
            restTemplate.postForObject(url, mapOf("chat_id" to chatId, "action" to action), String::class.java)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun answerCallbackQuery(callbackQueryId: String, text: String? = null) {
        val url = "$apiUrl/answerCallbackQuery"
        val body = mutableMapOf("callback_query_id" to callbackQueryId)
        text?.let { body["text"] = it }
        try {
            restTemplate.postForObject(url, body, String::class.java)
        } catch (e: Exception) {
            // ignore
        }
    }

    // ==================== KEYBOARDS ====================

    fun createInlineKeyboard(buttons: List<List<Map<String, String>>>): Map<String, Any> {
        return mapOf("inline_keyboard" to buttons)
    }

    fun createReplyKeyboard(buttons: List<List<String>>, resize: Boolean = true): Map<String, Any> {
        val keyboard = buttons.map { row ->
            row.map { text -> mapOf("text" to text) }
        }
        return mapOf("keyboard" to keyboard, "resize_keyboard" to resize)
    }

    fun removeReplyKeyboard(): Map<String, Any> {
        return mapOf("remove_keyboard" to true)
    }

    // ==================== MAIN MENU ====================

    fun sendMainMenu(chatId: String) {
        val text = """
            🌟 <b>Welcome to SkyFlight Bot!</b> 🌟
            
            Your personal assistant for booking flights.
            
            ✈️ <i>Find the best deals and book instantly</i>
            
            Use the buttons below to get started:
        """.trimIndent()

        val keyboard = createReplyKeyboard(listOf(
            listOf("🔍 SEARCH FLIGHTS"),
            listOf("📋 MY BOOKINGS", "⭐ MY POINTS"),
            listOf("🏷️ PROMO CODE", "❓ HELP")
        ))

        sendMessage(chatId, text, keyboard)
    }

    fun sendMainMenuInline(chatId: String, messageId: Int? = null) {
        val text = """
            🌟 <b>SkyFlight Bot - Main Menu</b>
            
            ┌─────────────────────────┐
            │ ✈️ Book flights easily   │
            │ ⭐ Earn bonus points     │
            │ 💰 Get exclusive deals  │
            └─────────────────────────┘
            
            <i>What would you like to do?</i>
        """.trimIndent()

        val buttons = listOf(
            listOf(
                mapOf("text" to "🔍 SEARCH", "callback_data" to "menu_search"),
                mapOf("text" to "📋 BOOKINGS", "callback_data" to "menu_bookings")
            ),
            listOf(
                mapOf("text" to "⭐ POINTS", "callback_data" to "menu_points"),
                mapOf("text" to "🏷️ PROMO", "callback_data" to "menu_promo")
            ),
            listOf(
                mapOf("text" to "❓ HELP", "callback_data" to "menu_help"),
                mapOf("text" to "ℹ️ ABOUT", "callback_data" to "menu_about")
            )
        )

        val replyMarkup = createInlineKeyboard(buttons)

        if (messageId != null) {
            editMessage(chatId, messageId, text, replyMarkup)
        } else {
            sendMessage(chatId, text, replyMarkup)
        }
    }

    // ==================== FLIGHT BUTTONS ====================

    fun createFlightButtons(flights: List<Any>, startIndex: Int = 0): List<List<Map<String, String>>> {
        val buttons = mutableListOf<List<Map<String, String>>>()

        flights.drop(startIndex).take(5).forEachIndexed { i, flight ->
            val flightMap = flight as? Map<*, *>
            val airline = flightMap?.get("airlineName") as? String ?: "Flight"
            val price = flightMap?.get("price") as? Number ?: 0
            buttons.add(listOf(
                mapOf(
                    "text" to "✈️ ${startIndex + i + 1}. $airline - $price₽",
                    "callback_data" to "flight_${startIndex + i + 1}"
                )
            ))
        }

        if (flights.size > 5) {
            val navButtons = mutableListOf<Map<String, String>>()
            if (startIndex > 0) {
                navButtons.add(mapOf("text" to "◀️ PREV", "callback_data" to "flights_prev"))
            }
            if (startIndex + 5 < flights.size) {
                navButtons.add(mapOf("text" to "NEXT ▶️", "callback_data" to "flights_next"))
            }
            if (navButtons.isNotEmpty()) {
                buttons.add(navButtons)
            }
        }

        buttons.add(listOf(mapOf("text" to "🔙 BACK TO MENU", "callback_data" to "back_to_menu")))
        return buttons
    }

    fun createSeatButtons(availableSeats: List<String>): List<List<Map<String, String>>> {
        val buttons = mutableListOf<List<Map<String, String>>>()

        availableSeats.chunked(4).forEach { row ->
            buttons.add(row.map { seat ->
                mapOf("text" to "💺 $seat", "callback_data" to "seat_$seat")
            })
        }

        buttons.add(listOf(mapOf("text" to "🔙 BACK", "callback_data" to "back")))
        return buttons
    }

    fun createConfirmButtons(): List<List<Map<String, String>>> {
        return listOf(
            listOf(
                mapOf("text" to "✅ CONFIRM BOOKING", "callback_data" to "confirm_yes"),
                mapOf("text" to "❌ CANCEL", "callback_data" to "confirm_no")
            ),
            listOf(
                mapOf("text" to "🔙 BACK", "callback_data" to "back")
            )
        )
    }

    fun createBookingButtons(bookings: List<Any>): List<List<Map<String, String>>> {
        val buttons = mutableListOf<List<Map<String, String>>>()

        bookings.take(5).forEach { booking ->
            val bookingMap = booking as? Map<*, *>
            val bookingId = bookingMap?.get("id") as? Long ?: return@forEach
            val status = bookingMap?.get("status") as? String ?: "UNKNOWN"

            if (status in listOf("PENDING_PAYMENT", "CONFIRMED")) {
                buttons.add(listOf(
                    mapOf("text" to "❌ CANCEL #$bookingId", "callback_data" to "cancel_$bookingId")
                ))
            }
        }

        buttons.add(listOf(mapOf("text" to "🔙 BACK TO MENU", "callback_data" to "back_to_menu")))
        return buttons
    }

    fun createPaymentButtons(bookingId: Long): List<List<Map<String, String>>> {
        return listOf(
            listOf(
                mapOf("text" to "💳 PAY NOW", "url" to "https://skyflightbooking.ru/payment?bookingId=$bookingId"),
                mapOf("text" to "❌ CANCEL", "callback_data" to "cancel_$bookingId")
            ),
            listOf(
                mapOf("text" to "🔙 BACK", "callback_data" to "back_to_menu")
            )
        )
    }

    fun createBackButton(): List<List<Map<String, String>>> {
        return listOf(
            listOf(mapOf("text" to "🔙 BACK TO MENU", "callback_data" to "back_to_menu"))
        )
    }
}
