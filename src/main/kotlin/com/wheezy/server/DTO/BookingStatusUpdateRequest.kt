package com.wheezy.server.DTO

import com.wheezy.server.Enums.BookingStatus

data class BookingStatusUpdateRequest(
    val status: BookingStatus
)
