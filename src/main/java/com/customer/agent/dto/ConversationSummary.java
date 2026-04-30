package com.customer.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationSummary {
    private String conversationSummary;
    private String ticketSummary;
    private String qualitySummary;
    private List<String> tags = new ArrayList<>();
}
