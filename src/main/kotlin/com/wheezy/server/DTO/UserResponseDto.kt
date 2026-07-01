package com.wheezy.server.DTO

import java.math.BigDecimal

data class UserResponseDto(
    val id: Long,
    val email: String,
    val name: String?,
    val profilePicture: String?,
    val countryCode: String? = null,
    val taxRate: BigDecimal? = null
)