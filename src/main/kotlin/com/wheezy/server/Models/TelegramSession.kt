package com.wheezy.server.Models

import com.wheezy.server.Models.Flight

data class TelegramSession(
    var currentStep: String = "idle",
    var flights: List<Flight>? = null,
    var selectedFlight: Flight? = null,
    var selectedSeat: String? = null,
    var fromCity: String? = null,
    var toCity: String? = null,
    var flightDate: String? = null,
    var promocode: String? = null,
    var pointsToUse: Int? = null,
    var bookingId: Long? = null,
    var lastMessageId: Int? = null
)
