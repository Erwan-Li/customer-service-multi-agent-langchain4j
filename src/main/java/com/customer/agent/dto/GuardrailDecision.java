package com.customer.agent.dto;

import lombok.Data;

@Data
public class GuardrailDecision {
    private boolean allowDirectAnswer = true;
    private boolean needMoreInfo;
    private boolean requiresManualReview;
    private boolean createTicket;
    private String safeReply = "";
    private String reviewIssue = "";
    private String riskFlag = "";
}
