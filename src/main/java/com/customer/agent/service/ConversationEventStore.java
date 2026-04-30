package com.customer.agent.service;

import com.customer.agent.dto.ConversationEvent;

import java.util.List;

public interface ConversationEventStore {

    void append(String conversationId, ConversationEvent event);

    List<ConversationEvent> list(String conversationId);

    long getLastActive(String conversationId);

    void clear(String conversationId);
}
