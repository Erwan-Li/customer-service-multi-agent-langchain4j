package com.customer.agent.dto;

import com.customer.agent.enums.PriorityLevel;
import com.customer.agent.enums.TicketStatus;

public record TicketView(
        String id,
        String category,
        PriorityLevel priority,
        TicketStatus status,
        String summary
) {
}
