package com.customer.agent.dto;

import java.util.List;

public record KnowledgeEvidenceView(
        String sourceType,
        String title,
        String content,
        double score,
        double retrievalScore,
        double rerankScore,
        List<String> matchReasons
) {
}
