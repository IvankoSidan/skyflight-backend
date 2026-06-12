package com.wheezy.server.DTO

data class AuthResponse(
    val user: UserResponseDto,
    val token: String
)

