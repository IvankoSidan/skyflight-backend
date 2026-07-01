package com.wheezy.server.DTO

import com.fasterxml.jackson.annotation.JsonProperty

data class GoogleAuthRequestDto(
    @JsonProperty("id_token")
    val id_token: String
)