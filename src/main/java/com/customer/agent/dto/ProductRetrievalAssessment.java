package com.customer.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductRetrievalAssessment {
    private List<KnowledgeEvidenceView> evidences = new ArrayList<>();
    private boolean grounded;
    private boolean requiresManualReview;
    private double confidence;
    private String matchedCategory = "unknown";
    private String supportRoute = "";
    private String likelyMatchedProduct = "";
    private String likelyMatchedGroup = "";
    private String confidenceLevel = "LOW";
    private String confidenceExplanation = "";
}
