package com.quickcart.chat.exception;

import org.springframework.http.HttpStatus;

/** 429 RATE_LIMITED (the one new code in the chatbot contract). */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many messages. Please wait a moment and try again.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}