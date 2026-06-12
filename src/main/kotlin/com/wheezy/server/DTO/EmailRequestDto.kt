package com.wheezy.server.DTO

data class EmailRequestDto(
    val to: String,
    val toName: String? = null,
    val subject: String,
    val htmlContent: String? = null,
    val templateId: Long? = null,
    val params: Map<String, Any>? = null
)
