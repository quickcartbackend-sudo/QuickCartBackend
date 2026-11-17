package com.quickcart.chat.memory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

/**
 * In-memory ChatMemoryRepository with an idle TTL and a per-user conversation cap.
 * Key format: "{userId}:{uuid}". Reads never extend the TTL; a save (a new message) does.
 */
public class ExpiringChatMemoryRepository implements ChatMemoryRepository {

    private record Entry(List<Message> messages, Instant touched) { }

    private final Map<String, Entry> store = new LinkedHashMap<>();   // iteration order = least recently saved first
    private final Clock clock;
    private final Duration ttl;
    private final int maxPerUser;

    public ExpiringChatMemoryRepository(Clock clock, Duration ttl, int maxPerUser) {
        Assert.notNull(clock, "clock");
        Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "ttl must be positive");
        Assert.isTrue(maxPerUser > 0, "maxPerUser must be positive");
        this.clock = clock;
        this.ttl = ttl;
        this.maxPerUser = maxPerUser;
    }

    @Override
    public synchronized List<String> findConversationIds() {
        purgeExpired();
        return List.copyOf(store.keySet());
    }

    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId");
        purgeExpired();
        Entry entry = store.get(conversationId);
        return entry == null ? List.of() : entry.messages();
    }

    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId");
        Assert.notNull(messages, "messages");
        purgeExpired();
        boolean isNew = !store.containsKey(conversationId);
        store.remove(conversationId);                                         // re-insert = move to the end
        store.put(conversationId, new Entry(List.copyOf(messages), clock.instant()));
        if (isNew) {
            evictOldestOfOwner(conversationId);
        }
    }

    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        if (conversationId != null) {
            store.remove(conversationId);
        }
    }

    /** True if the conversation exists and has not expired. Does not refresh the TTL. */
    public synchronized boolean contains(String conversationId) {
        purgeExpired();
        return conversationId != null && store.containsKey(conversationId);
    }

    // ---------------------------------------------------------------- internals

    private void purgeExpired() {
        Instant now = clock.instant();
        store.values().removeIf(e -> !e.touched().plus(ttl).isAfter(now));
    }

    private void evictOldestOfOwner(String newId) {
        String prefix = ownerOf(newId) + ":";
        List<String> mine = new ArrayList<>();
        for (String key : store.keySet()) {                                   // already oldest first
            if (key.startsWith(prefix)) {
                mine.add(key);
            }
        }
        int excess = mine.size() - maxPerUser;
        for (int i = 0; i < excess; i++) {
            store.remove(mine.get(i));                                        // the new key is last, never evicted
        }
    }

    private static String ownerOf(String conversationId) {
        int colon = conversationId.indexOf(':');
        return colon < 0 ? conversationId : conversationId.substring(0, colon);
    }
}