package com.customer.agent.service;

import com.customer.agent.dto.ConversationEvent;
import com.customer.agent.util.JsonHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisConversationEventStore implements ConversationEventStore {

    private static final String EVENT_KEY = "cs-agent:event:";
    private static final String ACTIVE_KEY = "cs-agent:active:";

    private final StringRedisTemplate redisTemplate;
    private final JsonHelper jsonHelper;

    @Value("${app.memory.event-ttl-seconds:86400}")
    private long ttlSeconds;

    @Override
    public void append(String conversationId, ConversationEvent event) {
        List<ConversationEvent> events = new ArrayList<>(list(conversationId));
        events.add(event);
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        redisTemplate.opsForValue().set(EVENT_KEY + conversationId, jsonHelper.writeValue(events), ttl);
        redisTemplate.opsForValue().set(ACTIVE_KEY + conversationId, String.valueOf(event.getTimestamp()), ttl);
    }

    @Override
    public List<ConversationEvent> list(String conversationId) {
        String json = redisTemplate.opsForValue().get(EVENT_KEY + conversationId);
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        Object value = jsonHelper.readMap("{\"items\":" + json + "}").get("items");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ConversationEvent> events = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                events.add(new ConversationEvent(
                        String.valueOf(map.get("role")),
                        String.valueOf(map.get("content")),
                        Long.parseLong(String.valueOf(map.get("timestamp")))));
            }
        }
        return events;
    }

    @Override
    public long getLastActive(String conversationId) {
        String value = redisTemplate.opsForValue().get(ACTIVE_KEY + conversationId);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(List.of(EVENT_KEY + conversationId, ACTIVE_KEY + conversationId));
    }
}
