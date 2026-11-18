package com.quickcart.chat.controller;

import com.quickcart.chat.dto.ChatRequest;
import com.quickcart.chat.dto.ChatResponse;
import com.quickcart.chat.exception.ApiException;
import com.quickcart.chat.security.AuthenticatedUser;
import com.quickcart.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("hasRole('CUSTOMER')")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping("/messages")
    public ChatResponse send(@AuthenticationPrincipal AuthenticatedUser user, Authentication authentication,
                             @Valid @RequestBody ChatRequest request) {
        if (!(authentication.getCredentials() instanceof String token) || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
        }
        return service.send(user.id(), token, request.conversationId(), request.message());
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String conversationId) {
        service.deleteConversation(user.id(), conversationId);
    }
}