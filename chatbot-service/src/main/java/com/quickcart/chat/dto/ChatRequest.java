package com.quickcart.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        String conversationId,
        @NotBlank @Size(max = 500) String message) {
}