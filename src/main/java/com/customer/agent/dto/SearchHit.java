package com.customer.agent.dto;

import java.util.Map;

public record SearchHit(
        String id,
        String content,
        String parentId,
        String parentContent,
        double score,
        Map<String, Object> metadata
) {
}
