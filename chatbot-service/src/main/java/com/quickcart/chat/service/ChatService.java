package com.quickcart.chat.service;

import com.quickcart.chat.dto.ChatResponse;
import com.quickcart.chat.exception.ApiException;
import com.quickcart.chat.memory.ExpiringChatMemoryRepository;
import com.quickcart.chat.tools.ShoppingTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_REPLY_CHARS = 2000;
    private static final String EMPTY_REPLY =
            "Sorry, I could not come up with an answer. Please try asking in a different way.";

    private final ChatClient chatClient;
    private final ExpiringChatMemoryRepository memory;
    private final UserRateLimiter rateLimiter;
    private final LlmGate llmGate;
    private final ShoppingTools tools;
    private final Clock clock;
    private final String systemPrompt;

    public ChatService(ChatClient chatClient, ExpiringChatMemoryRepository memory, UserRateLimiter rateLimiter,
                       LlmGate llmGate, ShoppingTools tools, Clock clock,
                       @Value("classpath:prompts/system.txt") Resource systemPromptResource) {
        this.chatClient = chatClient;
        this.memory = memory;
        this.rateLimiter = rateLimiter;
        this.llmGate = llmGate;
        this.tools = tools;
        this.clock = clock;
        try {
            this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompts/system.txt", e);
        }
        if (this.systemPrompt.isBlank()) {
            throw new IllegalStateException("prompts/system.txt is empty");
        }
    }

    /** POST /messages. The request is already validated. The token is only passed to the tools via ToolContext. */
    public ChatResponse send(long userId, String token, String conversationId, String message) {
        rateLimiter.check(userId);                                          // 429 before any model work
        String id = resolveConversationId(userId, conversationId);
        String key = key(userId, id);
        String reply = llmGate.run(() -> askModel(key, token, message.trim()));
        return new ChatResponse(id, limit(reply), Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }

    /** DELETE /conversations/{id}. Idempotent: always succeeds, whatever the id looks like. */
    public void deleteConversation(long userId, String conversationId) {
        if (conversationId != null && UUID_FORMAT.matcher(conversationId.trim()).matches()) {
            memory.deleteByConversationId(key(userId, conversationId.trim().toLowerCase(Locale.ROOT)));
        }
    }

    // ---------------------------------------------------------------- internals

    /** Null, malformed, unknown, expired or someone else's id all lead to a brand new conversation. */
    private String resolveConversationId(long userId, String clientId) {
        if (clientId != null && UUID_FORMAT.matcher(clientId.trim()).matches()) {
            String canonical = clientId.trim().toLowerCase(Locale.ROOT);
            if (memory.contains(key(userId, canonical))) {                  // the key always starts with MY user id
                return canonical;
            }
        }
        return UUID.randomUUID().toString();
    }

    private String askModel(String key, String token, String message) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, key))
                    .tools(tools)
                    .toolContext(Map.<String, Object>of(ShoppingTools.TOKEN_KEY, token))
                    .call()
                    .content();
        } catch (RuntimeException e) {                                      // Ollama down, model missing, timeout, ...
            log.warn("model call failed type={}", e.getClass().getSimpleName());   // never the message text
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The assistant is temporarily unavailable. Please try again.");
        }
    }

    private String limit(String reply) {
        if (reply == null || reply.isBlank()) {
            return EMPTY_REPLY;
        }
        String r = reply.strip();
        return r.length() > MAX_REPLY_CHARS ? r.substring(0, MAX_REPLY_CHARS - 3) + "..." : r;
    }

    private static String key(long userId, String id) {
        return userId + ":" + id;
    }
}