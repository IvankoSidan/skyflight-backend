package com.wheezy.server.DTO

data class UserResponseDto(
    val id: Long,
    val email: String,
    val name: String?,
    val profilePicture: String?
)
