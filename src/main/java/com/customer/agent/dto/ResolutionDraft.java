package com.customer.agent.dto;

import lombok.Data;

@Data
public class ResolutionDraft {
    private String customerReply;
    private String internalSuggestion;
    private boolean needMoreInfo;
    private String followupQuestion;
    private boolean createTicket;
    private String replyStrategy = "DIRECT";
}
