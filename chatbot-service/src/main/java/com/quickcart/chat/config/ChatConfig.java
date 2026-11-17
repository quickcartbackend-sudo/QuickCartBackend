package com.quickcart.chat.config;

import com.quickcart.chat.memory.ExpiringChatMemoryRepository;
import java.time.Clock;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ExpiringChatMemoryRepository chatMemoryRepository(Clock clock, ChatProperties props) {
        return new ExpiringChatMemoryRepository(clock, props.conversationTtl(), props.maxConversationsPerUser());
    }

    @Bean
    ChatMemory chatMemory(ExpiringChatMemoryRepository repository, ChatProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(props.memoryWindow())
                .build();
    }
}