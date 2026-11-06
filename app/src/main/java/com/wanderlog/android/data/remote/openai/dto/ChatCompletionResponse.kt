package com.wanderlog.android.data.remote.openai.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChoiceDto>
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    val message: MessageResponseDto,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageResponseDto(
    val role: String,
    val content: String
)
