package com.quickcart.chat.dto;

import java.time.Instant;

public record ChatResponse(String conversationId, String reply, Instant createdAt) {
}