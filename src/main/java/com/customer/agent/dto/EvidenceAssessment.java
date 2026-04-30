package com.customer.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EvidenceAssessment {
    private boolean sufficient;
    private String confidenceLevel = "LOW";
    private String reason = "";
    private List<String> missingFields = new ArrayList<>();
    private String nextAction = "RETRY_SEARCH";
    private String refinedQuery = "";
    private String supportRoute = "";
    private boolean requiresManualReview;
    private String likelyMatchedProduct = "";
    private String likelyMatchedGroup = "";
}
