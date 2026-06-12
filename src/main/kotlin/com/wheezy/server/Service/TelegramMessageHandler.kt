package com.wheezy.server.Service

import com.wheezy.server.Enums.BookingStatus
import com.wheezy.server.Models.Booking
import com.wheezy.server.Models.Flight
import com.wheezy.server.Models.TelegramSession
import com.wheezy.server.Repository.*
import com.wheezy.server.Service.NotificationSenderService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class TelegramMessageHandler(
    private val botService: TelegramBotService,
    private val flightRepository: FlightRepository,
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository,
    private val promocodeRepository: PromocodeRepository,
    private val userPointsRepository: UserPointsRepository,
    private val notificationSenderService: NotificationSenderService
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = mutableMapOf<String, TelegramSession>()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // ==================== MESSAGE HANDLER ====================

    fun handleMessage(message: Map<String, Any>) {
        val chat = message["chat"] as? Map<String, Any> ?: return
        val chatId = (chat["id"] as? Number)?.toString() ?: return
        val text = message["text"] as? String ?: return

        botService.sendChatAction(chatId)

        val session = sessions.getOrPut(chatId) { TelegramSession() }

        // Save message ID for later editing
        val messageId = (message["message_id"] as? Number)?.toInt()
        session.lastMessageId = messageId

        val response = when {
            text == "/start" || text == "/menu" -> {
                botService.sendMainMenuInline(chatId)
                return
            }
            text == "/help" -> handleHelp(chatId)
            text == "/bookings" -> handleMyBookings(chatId)
            text == "/points" -> handleMyPoints(chatId)
            text == "/cancel" -> handleCancelPrompt()

            // Reply keyboard buttons
            text == "🔍 SEARCH FLIGHTS" -> handleSearchPrompt()
            text == "📋 MY BOOKINGS" -> handleMyBookings(chatId)
            text == "⭐ MY POINTS" -> handleMyPoints(chatId)
            text == "🏷️ PROMO CODE" -> handlePromoPrompt()
            text == "❓ HELP" -> handleHelp(chatId)

            // Search commands
            text.lowercase().contains("find") || text.lowercase().contains("search") -> {
                handleSearchFlights(text, session, chatId)
            }
            text.lowercase().contains("promo") -> {
                handleApplyPromocode(text, session, chatId)
            }
            text.lowercase().contains("point") -> {
                handleUsePoints(text, session, chatId)
            }

            // Step-based handlers
            session.currentStep == "select_flight" -> {
                handleSelectFlightByNumber(text, session, chatId)
            }
            session.currentStep == "select_seat" -> {
                handleSelectSeatByNumber(text, session, chatId)
            }
            session.currentStep == "confirm" && text.equals("yes", ignoreCase = true) -> {
                handleConfirmBooking(session, chatId)
            }
            session.currentStep == "confirm" && text.equals("no", ignoreCase = true) -> {
                session.currentStep = "idle"
                Pair("❌ <b>Booking cancelled</b>", null)
            }
            else -> handleHelp(chatId)
        }

        if (response.second != null || response.first.isNotEmpty()) {
            botService.sendMessage(chatId, response.first, response.second)
        }
    }

    // ==================== CALLBACK HANDLER ====================

    fun handleCallback(callbackQuery: Map<String, Any>) {
        val data = callbackQuery["data"] as? String ?: return
        val callbackId = callbackQuery["id"] as? String ?: return
        val message = callbackQuery["message"] as? Map<String, Any> ?: return
        val chat = message["chat"] as? Map<String, Any> ?: return
        val chatId = (chat["id"] as? Number)?.toString() ?: return
        val messageId = (message["message_id"] as? Number)?.toInt() ?: return

        botService.answerCallbackQuery(callbackId)

        val session = sessions.getOrPut(chatId) { TelegramSession() }

        when {
            // Main menu
            data == "back_to_menu" -> {
                botService.sendMainMenuInline(chatId, messageId)
                session.currentStep = "idle"
            }
            data == "menu_search" -> {
                botService.editMessage(chatId, messageId, """
                    🔍 <b>SEARCH FLIGHTS</b>
                    
                    Please enter your search query:
                    
                    <i>Example:</i>
                    <code>Find flights from Moscow to Sochi</code>
                """.trimIndent(), null)
                session.currentStep = "search"
            }
            data == "menu_bookings" -> {
                val (text, _) = handleMyBookings(chatId)
                val buttons = botService.createBackButton()
                val replyMarkup = botService.createInlineKeyboard(buttons)
                botService.editMessage(chatId, messageId, text, replyMarkup)
            }
            data == "menu_points" -> {
                val (text, _) = handleMyPoints(chatId)
                val buttons = botService.createBackButton()
                val replyMarkup = botService.createInlineKeyboard(buttons)
                botService.editMessage(chatId, messageId, text, replyMarkup)
            }
            data == "menu_promo" -> {
                botService.editMessage(chatId, messageId, """
                    🏷️ <b>APPLY PROMO CODE</b>
                    
                    Enter your promo code:
                    
                    <i>Example:</i>
                    <code>Apply promo WELCOME10</code>
                """.trimIndent(), null)
            }
            data == "menu_help" -> {
                val (text, _) = handleHelp(chatId)
                val buttons = botService.createBackButton()
                val replyMarkup = botService.createInlineKeyboard(buttons)
                botService.editMessage(chatId, messageId, text, replyMarkup)
            }
            data == "menu_about" -> {
                val text = """
                    ℹ️ <b>ABOUT SKYFLIGHT</b>
                    
                    SkyFlight is a modern flight booking platform.
                    
                    ✈️ <b>Features:</b>
                    • Search and book flights
                    • Choose your preferred seat
                    • Earn bonus points
                    • Get exclusive discounts
                    
                    📱 <b>Download App:</b>
                    https://skyflightbooking.ru/download
                    
                    📧 <b>Support:</b>
                    support@skyflightbooking.ru
                """.trimIndent()
                val buttons = botService.createBackButton()
                val replyMarkup = botService.createInlineKeyboard(buttons)
                botService.editMessage(chatId, messageId, text, replyMarkup)
            }

            // Flight selection
            data.startsWith("flight_") -> {
                val index = data.substringAfter("flight_").toIntOrNull()?.minus(1) ?: return
                selectFlightByIndex(index, session, chatId, messageId)
            }

            // Seat selection
            data.startsWith("seat_") -> {
                val seat = data.substringAfter("seat_")
                selectSeat(seat, session, chatId, messageId)
            }

            // Confirmation
            data == "confirm_yes" -> {
                confirmBooking(session, chatId, messageId)
            }
            data == "confirm_no" -> {
                session.currentStep = "idle"
                botService.editMessage(chatId, messageId, "❌ <b>Booking cancelled</b>", null)
                botService.sendMainMenuInline(chatId)
            }

            // Cancel booking
            data.startsWith("cancel_") -> {
                val bookingId = data.substringAfter("cancel_").toLongOrNull() ?: return
                cancelBookingByCallback(bookingId, chatId, messageId, session)
            }

            // Navigation
            data == "flights_next" -> {
                val startIndex = (session.flights?.indexOf(session.selectedFlight)?.plus(1) ?: 0)
                    .coerceAtMost((session.flights?.size ?: 0) - 5)
                showFlightsPage(session, chatId, messageId, startIndex)
            }
            data == "flights_prev" -> {
                val startIndex = (session.flights?.indexOf(session.selectedFlight)?.minus(5) ?: 0).coerceAtLeast(0)
                showFlightsPage(session, chatId, messageId, startIndex)
            }
            data == "back" -> {
                if (session.selectedFlight != null) {
                    showFlightSelection(session, chatId, messageId)
                } else {
                    botService.sendMainMenuInline(chatId, messageId)
                }
            }
        }
    }

    // ==================== PRIVATE HANDLERS ====================

    private fun handleHelp(chatId: String): Pair<String, Map<String, Any>?> {
        val text = """
            ❓ <b>HELP - Available Commands</b>
            
            🔍 <b>Search flights</b>
            <code>Find flights from Moscow to Sochi</code>
            
            📋 <b>My bookings</b>
            <code>/bookings</code>
            
            ⭐ <b>My points</b>
            <code>/points</code>
            
            🏷️ <b>Apply promo code</b>
            <code>Apply promo WELCOME10</code>
            
            💰 <b>Use points</b>
            <code>Use 500 points</code>
            
            ❌ <b>Cancel booking</b>
            <code>/cancel 12345</code>
            
            ❓ <b>Help</b>
            <code>/help</code>
        """.trimIndent()
        return Pair(text, null)
    }

    private fun handleCancelPrompt(): Pair<String, Map<String, Any>?> {
        return Pair("❌ <b>Cancel Booking</b>\n\nSend: <code>/cancel [booking_id]</code>\n\nExample: <code>/cancel 12345</code>", null)
    }

    private fun handlePromoPrompt(): Pair<String, Map<String, Any>?> {
        return Pair("🏷️ <b>Apply Promo Code</b>\n\nSend: <code>Apply promo YOURCODE</code>", null)
    }

    private fun handleSearchPrompt(): Pair<String, Map<String, Any>?> {
        return Pair("🔍 <b>Search Flights</b>\n\nSend: <code>Find flights from [city] to [city]</code>\n\nExample: <code>Find flights from Moscow to Sochi</code>", null)
    }

    private fun handleSearchFlights(text: String, session: TelegramSession, chatId: String): Pair<String, Map<String, Any>?> {
        val regex = """(?:from|find|search)\s+([A-Za-zА-Яа-я-]+)\s+(?:to|for)\s+([A-Za-zА-Яа-я-]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(text)

        if (match == null) {
            return Pair("❌ <b>Could not parse cities.</b>\n\nExample: <code>Find flights from Moscow to Sochi</code>", null)
        }

        val fromCity = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
        val toCity = match.groupValues[2].trim().replaceFirstChar { it.uppercase() }

        session.fromCity = fromCity
        session.toCity = toCity

        val flights = flightRepository.findByDepartureCityAndArrivalCity(fromCity, toCity)

        if (flights.isEmpty()) {
            return Pair("❌ <b>No flights found from $fromCity to $toCity</b>\n\nTry different cities or dates.", null)
        }

        session.flights = flights
        session.currentStep = "select_flight"

        return showFlightSelection(session, chatId, null)
    }

    private fun showFlightSelection(session: TelegramSession, chatId: String, messageId: Int?): Pair<String, Map<String, Any>?> {
        val flights = session.flights ?: return Pair("", null)
        val fromCity = session.fromCity ?: return Pair("", null)
        val toCity = session.toCity ?: return Pair("", null)

        val message = buildString {
            appendLine("✈️ <b>Found ${flights.size} flights from $fromCity to $toCity</b>")
            appendLine()
            appendLine("<i>Select a flight:</i>")
            appendLine()
            flights.take(5).forEachIndexed { i, flight ->
                val num = i + 1
                appendLine("┌─────────────────────────────────┐")
                appendLine("│ <b>$num. ${flight.airlineName}</b>")
                appendLine("├─────────────────────────────────┤")
                appendLine("│ 🕐 ${flight.departureTime} → ${flight.arriveTime}")
                appendLine("│ 📍 ${flight.departureShort} → ${flight.arrivalShort}")
                appendLine("│ 💺 ${flight.classSeat}")
                appendLine("│ 💰 <b>${flight.price}₽</b>")
                appendLine("└─────────────────────────────────┘")
                appendLine()
            }
            if (flights.size > 5) {
                appendLine("<i>Showing 1-5 of ${flights.size} flights</i>")
                appendLine()
            }
            appendLine("👇 <b>Click a button below to select:</b>")
        }

        val buttons = botService.createFlightButtons(flights.map { it.toMap() }, 0)
        val replyMarkup = botService.createInlineKeyboard(buttons)

        return if (messageId != null) {
            botService.editMessage(chatId, messageId, message, replyMarkup)
            Pair("", null)
        } else {
            Pair(message, replyMarkup)
        }
    }

    private fun showFlightsPage(session: TelegramSession, chatId: String, messageId: Int, startIndex: Int) {
        val flights = session.flights ?: return
        val fromCity = session.fromCity ?: return
        val toCity = session.toCity ?: return

        val flightsToShow = flights.drop(startIndex).take(5)

        val message = buildString {
            appendLine("✈️ <b>Found ${flights.size} flights from $fromCity to $toCity</b>")
            appendLine()
            appendLine("<i>Select a flight:</i>")
            appendLine()
            flightsToShow.forEachIndexed { i, flight ->
                val num = startIndex + i + 1
                appendLine("┌─────────────────────────────────┐")
                appendLine("│ <b>$num. ${flight.airlineName}</b>")
                appendLine("├─────────────────────────────────┤")
                appendLine("│ 🕐 ${flight.departureTime} → ${flight.arriveTime}")
                appendLine("│ 📍 ${flight.departureShort} → ${flight.arrivalShort}")
                appendLine("│ 💺 ${flight.classSeat}")
                appendLine("│ 💰 <b>${flight.price}₽</b>")
                appendLine("└─────────────────────────────────┘")
                appendLine()
            }
            appendLine("<i>Showing ${startIndex + 1}-${minOf(startIndex + 5, flights.size)} of ${flights.size}</i>")
            appendLine()
            appendLine("👇 <b>Click a button below to select:</b>")
        }

        val buttons = botService.createFlightButtons(flights.map { it.toMap() }, startIndex)
        val replyMarkup = botService.createInlineKeyboard(buttons)

        botService.editMessage(chatId, messageId, message, replyMarkup)
    }

    private fun handleSelectFlightByNumber(text: String, session: TelegramSession, chatId: String): Pair<String, Map<String, Any>?> {
        val index = text.trim().toIntOrNull()?.minus(1) ?: -1
        val flights = session.flights ?: return Pair("❌ <b>Please search for flights first</b>", null)

        if (index !in 0 until flights.size) {
            return Pair("❌ <b>Invalid flight number</b>\n\nEnter number 1-${flights.size}", null)
        }

        selectFlightByIndex(index, session, chatId, 0)
        return Pair("", null)
    }

    private fun selectFlightByIndex(index: Int, session: TelegramSession, chatId: String, messageId: Int) {
        val flights = session.flights ?: return
        val selectedFlight = flights[index]
        session.selectedFlight = selectedFlight
        session.currentStep = "select_seat"

        val reservedSeats = selectedFlight.reservedSeats.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val allSeats = generateSeatMap(selectedFlight.totalSeats)
        val availableSeats = allSeats.filter { seat -> !reservedSeats.contains(seat) }.take(12)

        val message = buildString {
            appendLine("✅ <b>Flight Selected!</b>")
            appendLine()
            appendLine("┌─────────────────────────────────┐")
            appendLine("│ ✈️ <b>${selectedFlight.airlineName}</b>")
            appendLine("├─────────────────────────────────┤")
            appendLine("│ 📍 ${selectedFlight.departureCity} → ${selectedFlight.arrivalCity}")
            appendLine("│ 🕐 ${selectedFlight.departureTime} → ${selectedFlight.arriveTime}")
            appendLine("│ 📅 ${selectedFlight.flightDate.format(dateFormatter)}")
            appendLine("│ 💺 ${selectedFlight.classSeat}")
            appendLine("│ 💰 <b>${selectedFlight.price}₽</b>")
            appendLine("└─────────────────────────────────┘")
            appendLine()
            appendLine("💺 <b>Available Seats:</b>")
            appendLine(availableSeats.joinToString(", "))
            appendLine()
            appendLine("👇 <b>Select a seat:</b>")
        }

        val buttons = botService.createSeatButtons(availableSeats)
        val replyMarkup = botService.createInlineKeyboard(buttons)

        if (messageId > 0) {
            botService.editMessage(chatId, messageId, message, replyMarkup)
        } else {
            botService.sendMessage(chatId, message, replyMarkup)
        }
    }

    private fun handleSelectSeatByNumber(text: String, session: TelegramSession, chatId: String): Pair<String, Map<String, Any>?> {
        val seat = text.trim().uppercase()
        val flight = session.selectedFlight ?: return Pair("❌ <b>Please select a flight first</b>", null)

        val reservedSeats = flight.reservedSeats.split(",").map { it.trim() }
        if (reservedSeats.contains(seat)) {
            return Pair("❌ <b>Seat $seat is already taken</b>\n\nPlease choose another seat.", null)
        }

        selectSeat(seat, session, chatId, 0)
        return Pair("", null)
    }

    private fun selectSeat(seat: String, session: TelegramSession, chatId: String, messageId: Int) {
        val flight = session.selectedFlight ?: return

        session.selectedSeat = seat
        session.currentStep = "confirm"

        val message = buildString {
            appendLine("✅ <b>Seat $seat Selected!</b>")
            appendLine()
            appendLine("┌─────────────────────────────────┐")
            appendLine("│ 📋 <b>Booking Details</b>")
            appendLine("├─────────────────────────────────┤")
            appendLine("│ ✈️ ${flight.airlineName}")
            appendLine("│ 📍 ${flight.departureCity} → ${flight.arrivalCity}")
            appendLine("│ 📅 ${flight.flightDate.format(dateFormatter)}")
            appendLine("│ 🕐 ${flight.departureTime}")
            appendLine("│ 💺 Seat: $seat")
            appendLine("│ 💰 <b>Total: ${flight.price}₽</b>")
            appendLine("└─────────────────────────────────┘")
            appendLine()
            appendLine("👇 <b>Confirm your booking:</b>")
        }

        val buttons = botService.createConfirmButtons()
        val replyMarkup = botService.createInlineKeyboard(buttons)

        if (messageId > 0) {
            botService.editMessage(chatId, messageId, message, replyMarkup)
        } else {
            botService.sendMessage(chatId, message, replyMarkup)
        }
    }

    private fun handleConfirmBooking(session: TelegramSession, chatId: String): Pair<String, Map<String, Any>?> {
        val flight = session.selectedFlight ?: return Pair("❌ <b>No flight selected</b>", null)
        val seat = session.selectedSeat ?: return Pair("❌ <b>No seat selected</b>", null)

        val userId = getUserIdByChatId(chatId) ?: 1L

        try {
            val booking = Booking(
                userId = userId,
                flightId = flight.flightId,
                seatCount = 1,
                seatNumbers = seat,
                status = BookingStatus.PENDING_PAYMENT,
                bookingDate = LocalDateTime.now(),
                promocodeId = null
            )

            val savedBooking = bookingRepository.save(booking)
            session.bookingId = savedBooking.id

            val paymentUrl = "https://skyflightbooking.ru/payment?bookingId=${savedBooking.id}"

            val message = buildString {
                appendLine("✅ <b>BOOKING CREATED!</b>")
                appendLine()
                appendLine("┌─────────────────────────────────┐")
                appendLine("│ 📋 <b>Booking #${savedBooking.id}</b>")
                appendLine("├─────────────────────────────────┤")
                appendLine("│ ✈️ ${flight.airlineName}")
                appendLine("│ 📍 ${flight.departureCity} → ${flight.arrivalCity}")
                appendLine("│ 📅 ${flight.flightDate.format(dateFormatter)}")
                appendLine("│ 🕐 ${flight.departureTime}")
                appendLine("│ 💺 Seat: $seat")
                appendLine("│ 💰 <b>${flight.price}₽</b>")
                appendLine("└─────────────────────────────────┘")
                appendLine()
                appendLine("🔗 <b>Complete Payment:</b>")
                appendLine("<a href=\"$paymentUrl\">Click here to pay</a>")
                appendLine()
                appendLine("<i>After payment, your ticket will be sent to your email</i>")
                appendLine()
                appendLine("✨ <b>Thank you for choosing SkyFlight!</b>")
            }

            val buttons = botService.createPaymentButtons(savedBooking.id)
            val replyMarkup = botService.createInlineKeyboard(buttons)

            try {
                notificationSenderService.sendBookingCreated(userId, savedBooking.id)
            } catch (e: Exception) {
                logger.error("Failed to send notification", e)
            }

            session.currentStep = "idle"
            session.selectedFlight = null
            session.selectedSeat = null

            return Pair(message, replyMarkup)

        } catch (e: Exception) {
            logger.error("Error creating booking", e)
            return Pair("❌ <b>Error creating booking</b>\n\nPlease try again later.", null)
        }
    }

    private fun confirmBooking(session: TelegramSession, chatId: String, messageId: Int) {
        val (message, replyMarkup) = handleConfirmBooking(session, chatId)
        botService.editMessage(chatId, messageId, message, replyMarkup)
        session.currentStep = "idle"
    }

    private fun handleMyBookings(chatId: String): Pair<String, Map<String, Any>?> {
        val userId = getUserIdByChatId(chatId) ?: return Pair("❌ <b>User not found</b>\n\nPlease login to the app first", null)

        val bookings = bookingRepository.findByUserId(userId)

        if (bookings.isEmpty()) {
            return Pair("📋 <b>You have no bookings yet</b>\n\nUse /search to find flights!", null)
        }

        val message = buildString {
            appendLine("📋 <b>YOUR BOOKINGS</b>")
            appendLine()
            bookings.forEach { booking ->
                val flight = flightRepository.findById(booking.flightId).orElse(null)
                val statusEmoji = when (booking.status) {
                    BookingStatus.CONFIRMED -> "✅"
                    BookingStatus.PENDING_PAYMENT -> "⏳"
                    BookingStatus.CANCELED -> "❌"
                    else -> "📝"
                }
                val statusText = when (booking.status) {
                    BookingStatus.CONFIRMED -> "Confirmed"
                    BookingStatus.PENDING_PAYMENT -> "Pending Payment"
                    BookingStatus.CANCELED -> "Cancelled"
                    else -> booking.status.name
                }

                appendLine("┌─────────────────────────────────┐")
                appendLine("│ $statusEmoji <b>Booking #${booking.id}</b>")
                appendLine("├─────────────────────────────────┤")
                if (flight != null) {
                    appendLine("│ ✈️ ${flight.airlineName}")
                    appendLine("│ 📍 ${flight.departureCity} → ${flight.arrivalCity}")
                    appendLine("│ 📅 ${flight.flightDate.format(dateFormatter)}")
                    appendLine("│ 💺 ${booking.seatNumbers}")
                }
                appendLine("│ 📊 Status: $statusText")
                if (booking.status == BookingStatus.PENDING_PAYMENT) {
                    appendLine("│ 🔗 <a href=\"https://skyflightbooking.ru/payment?bookingId=${booking.id}\">Pay Now</a>")
                }
                appendLine("└─────────────────────────────────┘")
                appendLine()
            }
        }

        val buttons = botService.createBookingButtons(bookings.map { it.toMap() })
        val replyMarkup = if (buttons.size > 1) botService.createInlineKeyboard(buttons) else null

        return Pair(message, replyMarkup)
    }

    private fun handleMyPoints(chatId: String): Pair<String, Map<String, Any>?> {
        val userId = getUserIdByChatId(chatId) ?: return Pair("❌ <b>User not found</b>\n\nPlease login to the app first", null)

        val userPoints = userPointsRepository.findByUserId(userId).orElse(null)

        if (userPoints == null) {
            return Pair("⭐ <b>You have no bonus points yet</b>\n\nBook flights to earn points!", null)
        }

        val message = buildString {
            appendLine("⭐ <b>LOYALTY PROGRAM</b>")
            appendLine()
            appendLine("┌─────────────────────────────────┐")
            appendLine("│ 💰 <b>Balance:</b> ${userPoints.balance} points")
            appendLine("│ 🏆 <b>Lifetime:</b> ${userPoints.lifetimePoints} points")
            appendLine("│ 👑 <b>Tier:</b> ${userPoints.tier}")
            appendLine("└─────────────────────────────────┘")
            appendLine()
            appendLine("<b>How to earn points:</b>")
            appendLine("• ✈️ 5% cashback on flights")
            appendLine("• ⭐ 200 points per review")
            appendLine("• 👥 500 points per referral")
            appendLine()
            appendLine("💡 <i>100 points = 100₽ discount</i>")
        }

        return Pair(message, null)
    }

    private fun handleApplyPromocode(text: String, session: TelegramSession, chatId: String): Pair<String, Map<String, Any>?> {
        val regex = """(?:promo|discount)\s+(\w+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(text)

        if (match == null) {
            return Pair("🏷️ <b>Enter promo code:</b>\n\nExample: <code>Apply promo WELCOME10</code>", null)
        }

        val promocode = match.groupValues[1].uppercase()

        try {
            val promo = promocodeRepository.findValidPromocode(promocode, LocalDateTime.now())

            if (promo == null) {
                return Pair("❌ <b>Promo code $promocode is invalid or expired</b>", null)
            }

            val discount = promo.discountPercent ?: 0
            session.promocode = promocode

            val message = buildString {
                appendLine("✅ <b>Promo code $promocode applied!</b>")
                if (discount > 0) {
                    appendLine("🎉 You get <b>$discount% discount</b> on your booking!")
                }
                appendLine()
                appendLine("Continue with your booking or start a new search")
            }

            return Pair(message, null)

        } catch (e: Exception) {
            logger.error("Error applying promo code", e)
            return Pair("❌ <b>Error applying promo code</b>\n\nPlease try again", null)
        }
    }

    private fun handleUsePoints(text: String, session: TelegramSession, chatId: String): Pair<String, Map<String, Any>?> {
        val regex = """(?:use|redeem)\s+(\d+)\s+points?""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(text)

        if (match == null) {
            return Pair("⭐ <b>How many points to use?</b>\n\nExample: <code>Use 500 points</code>", null)
        }

        val points = match.groupValues[1].toIntOrNull() ?: return Pair("❌ <b>Invalid points amount</b>", null)

        val userId = getUserIdByChatId(chatId) ?: 1L
        val userPoints = userPointsRepository.findByUserId(userId).orElse(null)

        if (userPoints == null || userPoints.balance < points) {
            return Pair("❌ <b>Not enough points</b>\n\nYou have ${userPoints?.balance ?: 0} points available", null)
        }

        val discount = points / 100 * 100
        session.pointsToUse = points

        val message = buildString {
            appendLine("✅ <b>$points points will be used!</b>")
            appendLine("💰 Discount: <b>${discount}₽</b>")
            appendLine("⭐ Remaining: <b>${userPoints.balance - points}</b> points")
            appendLine()
            appendLine("Continue with your booking?")
        }

        return Pair(message, null)
    }

    private fun cancelBookingByCallback(bookingId: Long, chatId: String, messageId: Int, session: TelegramSession) {
        val (message, _) = cancelBooking(bookingId)
        botService.editMessage(chatId, messageId, message, null)
        session.currentStep = "idle"
        botService.sendMainMenuInline(chatId)
    }

    private fun cancelBooking(bookingId: Long): Pair<String, Map<String, Any>?> {
        try {
            val booking = bookingRepository.findById(bookingId).orElse(null)

            if (booking == null) {
                return Pair("❌ <b>Booking #$bookingId not found</b>", null)
            }

            if (booking.status == BookingStatus.CANCELED) {
                return Pair("ℹ️ <b>Booking #$bookingId is already cancelled</b>", null)
            }

            if (booking.status == BookingStatus.CONFIRMED) {
                return Pair("ℹ️ <b>Please contact support to cancel confirmed bookings</b>", null)
            }

            booking.status = BookingStatus.CANCELED
            booking.canceledAt = LocalDateTime.now()
            bookingRepository.save(booking)

            return Pair("✅ <b>Booking #$bookingId has been cancelled</b>", null)

        } catch (e: Exception) {
            logger.error("Error cancelling booking", e)
            return Pair("❌ <b>Error cancelling booking</b>\n\nPlease try again later", null)
        }
    }

    // ==================== HELPER METHODS ====================

    private fun generateSeatMap(totalSeats: Int): List<String> {
        val seats = mutableListOf<String>()
        val rows = (totalSeats / 6) + 2
        val letters = listOf("A", "B", "C", "D", "E", "F")

        for (row in 1..rows) {
            for (letter in letters) {
                seats.add("$row$letter")
            }
        }
        return seats.take(totalSeats)
    }

    private fun getUserIdByChatId(chatId: String): Long? {
        // TODO: Store chatId -> userId mapping in database
        return userRepository.findAll().firstOrNull()?.id
    }

    private fun Flight.toMap(): Map<String, Any> {
        return mapOf(
            "flightId" to flightId,
            "airlineName" to airlineName,
            "airlineLogo" to airlineLogo,
            "departureCity" to departureCity,
            "arrivalCity" to arrivalCity,
            "departureTime" to departureTime,
            "arriveTime" to arriveTime,
            "departureShort" to departureShort,
            "arrivalShort" to arrivalShort,
            "flightDate" to flightDate.toString(),
            "classSeat" to classSeat,
            "price" to price,
            "totalSeats" to totalSeats,
            "reservedSeats" to reservedSeats
        )
    }

    private fun Booking.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "flightId" to flightId,
            "seatNumbers" to seatNumbers,
            "seatCount" to seatCount,
            "status" to status.name,
            "bookingDate" to bookingDate.toString(),
            "paidAmount" to (paidAmount ?: 0)
        )
    }
}
