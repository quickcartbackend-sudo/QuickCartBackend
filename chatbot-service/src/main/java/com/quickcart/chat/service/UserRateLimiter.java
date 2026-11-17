package com.quickcart.chat.service;

import com.quickcart.chat.config.ChatProperties;
import com.quickcart.chat.exception.RateLimitedException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UserRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final int SWEEP_THRESHOLD = 1_000;

    private final Map<Long, Deque<Long>> hits = new HashMap<>();
    private final Clock clock;
    private final int limit;

    public UserRateLimiter(Clock clock, ChatProperties props) {
        this.clock = clock;
        this.limit = props.rateLimitPerMinute();
    }

    /** Records one attempt, or throws RateLimitedException with the seconds to wait. */
    public synchronized void check(long userId) {
        long now = clock.millis();
        Deque<Long> window = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
            window.pollFirst();
        }
        if (window.size() >= limit) {
            long waitMs = window.peekFirst() + WINDOW_MS - now;
            throw new RateLimitedException(Math.max(1, (waitMs + 999) / 1000));
        }
        window.addLast(now);
        if (hits.size() > SWEEP_THRESHOLD) {                      // forget users with no recent activity
            hits.values().removeIf(d -> d.isEmpty() || now - d.peekLast() >= WINDOW_MS);
        }
    }
}