package com.quickcart.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "quickcart.chat")
public record ChatProperties(
        // @DefaultValue("500") int maxMessageLength,
        @DefaultValue("10") int memoryWindow,
        @DefaultValue("30m") Duration conversationTtl,
        @DefaultValue("3") int maxConversationsPerUser,
        @DefaultValue("10") int rateLimitPerMinute,
        @DefaultValue("2") int llmConcurrency,
        @DefaultValue("20s") Duration llmQueueWait,
        @DefaultValue("90s") Duration llmTimeout) {
}