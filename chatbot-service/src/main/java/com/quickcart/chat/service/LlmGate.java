package com.quickcart.chat.service;

import com.quickcart.chat.config.ChatProperties;
import com.quickcart.chat.exception.ApiException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LlmGate {

    private final Semaphore permits;
    private final Duration queueWait;

    public LlmGate(ChatProperties props) {
        this.permits = new Semaphore(props.llmConcurrency(), true);     // fair: first come, first served
        this.queueWait = props.llmQueueWait();
    }

    public <T> T run(Supplier<T> call) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(queueWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        if (!acquired) {
            throw busy();
        }
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    private ApiException busy() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "The assistant is busy right now. Please try again in a moment.");
    }
}