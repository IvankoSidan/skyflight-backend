package com.wheezy.server.DTO

data class PromocodeRequest(
    val code: String,
    val amount: Long,
    val currency: String = "USD"
)
