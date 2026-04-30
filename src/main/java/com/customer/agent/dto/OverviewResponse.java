package com.customer.agent.dto;

import java.util.List;

public record OverviewResponse(
        List<ConversationView> conversations,
        List<TicketView> tickets
) {
}
