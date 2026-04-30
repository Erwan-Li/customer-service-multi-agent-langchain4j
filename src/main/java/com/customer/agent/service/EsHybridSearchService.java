package com.customer.agent.service;

import com.customer.agent.dto.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsHybridSearchService {

    public static final String DOC_TYPE_FAQ = "knowledge_faq";
    public static final String DOC_TYPE_SOP = "knowledge_sop";
    public static final String DOC_TYPE_TICKET_MEMORY = "ticket_memory";
    public static final String DOC_TYPE_USER_PROFILE = "user_profile";
    public static final String DOC_TYPE_QA_FEEDBACK = "qa_feedback";

    private final RestClient restClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    @Value("${app.elasticsearch.index-name}")
    private String indexName;

    @Value("${app.elasticsearch.embedding-dimension:1536}")
    private int embeddingDimension;

    @Value("${app.elasticsearch.vector-min-score:0.65}")
    private double vectorMinScore;

    @PostConstruct
    public void ensureIndex() {
        ensureIndexExists();
    }

    public void ensureIndexExists() {
        try {
            Response head = restClient.performRequest(new Request("HEAD", "/" + indexName));
            if (head.getStatusLine().getStatusCode() == 200) {
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            Map<String, Object> mapping = Map.of(
                    "mappings", Map.of(
                            "properties", Map.of(
                                    "docType", Map.of("type", "keyword"),
                                    "userId", Map.of("type", "keyword"),
                                    "conversationId", Map.of("type", "keyword"),
                                    "topic", Map.of("type", "keyword"),
                                    "content", Map.of("type", "text", "analyzer", "standard"),
                                    "parentContent", Map.of("type", "text", "analyzer", "standard"),
                                    "structuredJson", Map.of("type", "object", "enabled", true),
                                    "embedding", Map.of("type", "dense_vector", "dims", embeddingDimension, "index", true, "similarity", "cosine"),
                                    "createdAt", Map.of("type", "date")
                            )));
            Request request = new Request("PUT", "/" + indexName);
            request.setJsonEntity(objectMapper.writeValueAsString(mapping));
            restClient.performRequest(request);
            log.info("Created Elasticsearch index {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to create ES index {}", indexName, e);
        }
    }

    public void resetIndex() {
        try {
            restClient.performRequest(new Request("DELETE", "/" + indexName));
            log.info("Deleted Elasticsearch index {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to delete ES index {}, will try recreate anyway", indexName, e);
        }
        ensureIndexExists();
    }

    public void indexKnowledge(String docType, String parentId, String topic, String parentContent, String content) {
        Map<String, Object> document = baseDocument(docType, topic, null, null, parentContent, content, Map.of());
        indexDocument(parentId + "-" + Math.abs(content.hashCode()), document);
    }

    public void indexTicketMemory(String userId, String conversationId, String topic, String content, Map<String, Object> structuredJson) {
        Map<String, Object> document = baseDocument(DOC_TYPE_TICKET_MEMORY, topic, userId, conversationId, content, content, structuredJson);
        indexDocument("ticket-memory-" + UUID.randomUUID(), document);
    }

    public void indexUserProfile(String userId, String content, Map<String, Object> structuredJson) {
        Map<String, Object> document = baseDocument(DOC_TYPE_USER_PROFILE, "profile", userId, null, content, content, structuredJson);
        indexDocument("user-profile-" + userId, document);
    }

    public void indexQaFeedback(String conversationId, String userId, String content, Map<String, Object> structuredJson) {
        Map<String, Object> document = baseDocument(DOC_TYPE_QA_FEEDBACK, "qa-feedback", userId, conversationId, content, content, structuredJson);
        indexDocument("qa-feedback-" + UUID.randomUUID(), document);
    }

    public List<SearchHit> latestByDocType(String docType, String userId, int topK) {
        try {
            Map<String, Object> body = Map.of(
                    "size", topK,
                    "sort", List.of(Map.of("createdAt", Map.of("order", "desc"))),
                    "query", boolFilter(docType, userId));
            return search(body, 0);
        } catch (Exception e) {
            log.warn("Latest search failed for {}", docType, e);
            return List.of();
        }
    }

    public List<SearchHit> hybridSearch(String docType, String userId, String query, int topK) {
        List<SearchHit> vectorHits = vectorSearch(docType, userId, query, topK);
        List<SearchHit> keywordHits = keywordSearch(docType, userId, query, topK);
        return reciprocalRankFusion(vectorHits, keywordHits, topK);
    }

    private Map<String, Object> baseDocument(String docType, String topic, String userId, String conversationId,
                                             String parentContent, String content, Map<String, Object> structuredJson) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("docType", docType);
        document.put("topic", topic);
        document.put("userId", userId);
        document.put("conversationId", conversationId);
        document.put("content", content);
        document.put("parentContent", parentContent);
        document.put("structuredJson", structuredJson);
        document.put("embedding", embed(content));
        document.put("createdAt", Instant.now().toString());
        return document;
    }

    private void indexDocument(String id, Map<String, Object> document) {
        try {
            Request request = new Request("PUT", "/" + indexName + "/_doc/" + id);
            request.setJsonEntity(objectMapper.writeValueAsString(document));
            restClient.performRequest(request);
        } catch (Exception e) {
            log.warn("Failed to index document {}", id, e);
        }
    }

    private List<Float> embed(String text) {
        float[] vector = embeddingModel.embed(text).content().vector();
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private List<SearchHit> vectorSearch(String docType, String userId, String query, int topK) {
        try {
            Map<String, Object> body = Map.of(
                    "size", topK,
                    "query", Map.of(
                            "script_score", Map.of(
                                    "query", boolFilter(docType, userId),
                                    "script", Map.of(
                                            "source", "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
                                            "params", Map.of("query_vector", embed(query))))));
            return search(body, vectorMinScore);
        } catch (Exception e) {
            log.warn("Vector search failed", e);
            return List.of();
        }
    }

    private List<SearchHit> keywordSearch(String docType, String userId, String query, int topK) {
        try {
            Map<String, Object> body = Map.of(
                    "size", topK,
                    "query", Map.of(
                            "bool", Map.of(
                                    "filter", filters(docType, userId),
                                    "must", List.of(Map.of(
                                            "multi_match", Map.of(
                                                    "query", query,
                                                    "fields", List.of("content^2", "parentContent", "topic")))))));
            return search(body, 0);
        } catch (Exception e) {
            log.warn("Keyword search failed", e);
            return List.of();
        }
    }

    private Map<String, Object> boolFilter(String docType, String userId) {
        return Map.of("bool", Map.of("filter", filters(docType, userId)));
    }

    private List<Map<String, Object>> filters(String docType, String userId) {
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("docType", docType)));
        if (StringUtils.hasText(userId)) {
            filters.add(Map.of("term", Map.of("userId", userId)));
        }
        return filters;
    }

    private List<SearchHit> search(Map<String, Object> body, double minScore) throws IOException {
        Request request = new Request("POST", "/" + indexName + "/_search");
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        Response response = restClient.performRequest(request);
        String json = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode hits = objectMapper.readTree(json).path("hits").path("hits");
        List<SearchHit> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            double score = hit.path("_score").asDouble(0);
            if (score < minScore) {
                continue;
            }
            JsonNode source = hit.path("_source");
            Map<String, Object> metadata = objectMapper.convertValue(source, Map.class);
            results.add(new SearchHit(
                    hit.path("_id").asText(),
                    source.path("content").asText(),
                    null,
                    source.path("parentContent").asText(),
                    score,
                    metadata));
        }
        return results;
    }

    private List<SearchHit> reciprocalRankFusion(List<SearchHit> first, List<SearchHit> second, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, SearchHit> hitMap = new HashMap<>();
        int k = 60;
        accumulate(scores, hitMap, first, k);
        accumulate(scores, hitMap, second, k);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> hitMap.get(entry.getKey()))
                .toList();
    }

    private void accumulate(Map<String, Double> scores, Map<String, SearchHit> hitMap, List<SearchHit> hits, int k) {
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            hitMap.putIfAbsent(hit.id(), hit);
            scores.put(hit.id(), scores.getOrDefault(hit.id(), 0.0) + 1.0 / (k + i + 1));
        }
    }
}
