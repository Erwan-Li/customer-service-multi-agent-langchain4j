package com.customer.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeBundle {
    private List<KnowledgeEvidenceView> evidences = new ArrayList<>();
    private String supportRoute;
    private String historicalInsight;
    private boolean grounded;
    private boolean requiresManualReview;
    private double confidence;
    private String matchedCategory = "unknown";
    private String likelyMatchedProduct = "";
    private String likelyMatchedGroup = "";
    private String confidenceLevel = "LOW";
    private String confidenceExplanation = "";
    private List<String> reactSteps = new ArrayList<>();
}
