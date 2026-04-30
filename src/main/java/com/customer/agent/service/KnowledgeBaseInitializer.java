package com.customer.agent.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseInitializer {

    private final EsHybridSearchService esHybridSearchService;

    @Value("classpath:knowledge/customer-service-knowledge.md")
    private Resource knowledgeResource;

    @PostConstruct
    public void init() {
        try {
            String content = new String(knowledgeResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<String> blocks = split(content);
            for (String block : blocks) {
                String header = block.lines().findFirst().orElse("## FAQ | General").replace("##", "").trim();
                String[] parts = header.split("\\|");
                String kind = parts.length > 0 ? parts[0].trim().toUpperCase() : "FAQ";
                String topic = parts.length > 1 ? parts[1].trim() : "General";
                String docType = "SOP".equals(kind) ? EsHybridSearchService.DOC_TYPE_SOP : EsHybridSearchService.DOC_TYPE_FAQ;
                String parentId = UUID.randomUUID().toString();
                for (String chunk : chunk(block, 320, 40)) {
                    esHybridSearchService.indexKnowledge(docType, parentId, topic, block, chunk);
                }
            }
            log.info("Knowledge base initialized. blocks={}", blocks.size());
        } catch (Exception e) {
            log.warn("Failed to initialize knowledge base", e);
        }
    }

    private List<String> split(String raw) {
        String[] pieces = raw.split("(?=\\n##\\s)");
        List<String> results = new ArrayList<>();
        for (String piece : pieces) {
            if (!piece.isBlank()) {
                results.add(piece.trim());
            }
        }
        return results;
    }

    private List<String> chunk(String text, int size, int overlap) {
        if (text.length() <= size) {
            return List.of(text);
        }
        List<String> results = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            results.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return results;
    }
}
