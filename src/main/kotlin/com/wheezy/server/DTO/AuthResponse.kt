package com.wheezy.server.DTO

data class AuthResponse(
    val user: UserResponseDto? = null,
    val token: String? = null
)