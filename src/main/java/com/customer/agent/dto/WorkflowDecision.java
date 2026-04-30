package com.customer.agent.dto;

public record WorkflowDecision(
        boolean needsMoreInfo,
        boolean createTicket,
        boolean escalated,
        boolean readyToSend,
        boolean manualReviewRequired,
        String nextAction,
        String reason,
        String followupQuestion
) {
}
