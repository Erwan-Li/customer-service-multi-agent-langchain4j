package com.customer.agent.service;

import com.customer.agent.dto.SearchHit;
import com.customer.agent.util.JsonHelper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsLongTermMemoryService implements LongTermMemoryService {

    private final ChatModel chatModel;
    private final EsHybridSearchService esHybridSearchService;
    private final JsonHelper jsonHelper;

    @Override
    public String loadUserProfile(String userId) {
        return esHybridSearchService.latestByDocType(EsHybridSearchService.DOC_TYPE_USER_PROFILE, userId, 1)
                .stream()
                .findFirst()
                .map(SearchHit::content)
                .orElse("");
    }

    @Override
    public List<String> recallHistoricalTickets(String userId, String query, int topK) {
        return esHybridSearchService.hybridSearch(EsHybridSearchService.DOC_TYPE_TICKET_MEMORY, userId, query, topK)
                .stream()
                .map(SearchHit::content)
                .toList();
    }

    @Override
    public void rememberConversation(String conversationId, String userId, String content, String summary, List<String> tags) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(content)) {
            return;
        }
        String existingProfile = loadUserProfile(userId);
        Map<String, Object> extracted = extractProfile(existingProfile, content);
        String profileText = String.valueOf(extracted.getOrDefault("userProfile", ""));
        if (StringUtils.hasText(profileText)) {
            esHybridSearchService.indexUserProfile(userId, profileText, extracted);
        }
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("summary", summary);
        memory.put("tags", tags);
        memory.put("conversationId", conversationId);
        esHybridSearchService.indexTicketMemory(userId, conversationId, String.join(",", tags), content + "\n\nSummary:\n" + summary, memory);
    }

    @Override
    public void rememberQaFeedback(String conversationId, String userId, String content, List<String> issues) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        esHybridSearchService.indexQaFeedback(conversationId, userId, content, Map.of("issues", issues));
    }

    private Map<String, Object> extractProfile(String existingProfile, String content) {
        String prompt = """
                Extract stable customer profile from a customer support conversation.
                Return JSON only.

                Existing profile:
                %s

                New content:
                %s

                JSON schema:
                {
                  "userProfile": "short profile summary with preferences and sensitivity",
                  "riskNotes": ["stable complaint or risk notes if any"]
                }
                """.formatted(empty(existingProfile), abbreviate(content, 2500));
        try {
            return jsonHelper.readMap(chatModel.chat(prompt));
        } catch (Exception e) {
            log.warn("Profile extraction failed", e);
            return Map.of("userProfile", "High-touch customer support profile pending refinement.");
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private String empty(String value) {
        return value == null ? "" : value;
    }
}
