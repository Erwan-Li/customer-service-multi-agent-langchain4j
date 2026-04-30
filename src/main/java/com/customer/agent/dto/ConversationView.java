package com.customer.agent.dto;

import com.customer.agent.enums.ConversationStatus;

public record ConversationView(
        String id,
        String userId,
        ConversationStatus status,
        String intent,
        String subIntent,
        String lastMessagePreview
) {
}
