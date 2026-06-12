package com.wheezy.server.DTO

data class GoogleUserDto(
    val googleId: String,
    val email: String,
    val name: String?,
    val profilePicture: String?
)
