package com.wheezy.server.DTO

data class UserRegisterDto(
    val email: String,
    val password: String,
    val name: String? = null
)
