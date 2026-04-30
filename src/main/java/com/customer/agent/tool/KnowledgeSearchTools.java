package com.customer.agent.tool;

import com.customer.agent.dto.KnowledgeEvidenceView;
import com.customer.agent.dto.SearchHit;
import com.customer.agent.service.EsHybridSearchService;
import com.customer.agent.service.LongTermMemoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchTools {

    private final EsHybridSearchService esHybridSearchService;
    private final LongTermMemoryService longTermMemoryService;

    @Tool("Search customer service FAQ knowledge by semantic and keyword hybrid retrieval")
    public List<KnowledgeEvidenceView> searchFaq(@P("Search query") String query, @P("Maximum results") int topK) {
        return esHybridSearchService.hybridSearch(EsHybridSearchService.DOC_TYPE_FAQ, null, query, topK).stream()
                .map(hit -> toEvidence("FAQ", hit))
                .toList();
    }

    @Tool("Search customer service SOP and internal handling procedures")
    public List<KnowledgeEvidenceView> searchSop(@P("Search query") String query, @P("Maximum results") int topK) {
        return esHybridSearchService.hybridSearch(EsHybridSearchService.DOC_TYPE_SOP, null, query, topK).stream()
                .map(hit -> toEvidence("SOP", hit))
                .toList();
    }

    @Tool("Recall historical tickets and customer service memory for a user")
    public List<KnowledgeEvidenceView> recallHistoricalTickets(@P("User id") String userId,
                                                               @P("Search query") String query,
                                                               @P("Maximum results") int topK) {
        return longTermMemoryService.recallHistoricalTickets(userId, query, topK).stream()
                .map(memory -> new KnowledgeEvidenceView(
                        "HISTORY",
                        "历史工单记忆",
                        memory,
                        1.0,
                        1.0,
                        0.0,
                        List.of("历史记忆")))
                .toList();
    }

    private KnowledgeEvidenceView toEvidence(String sourceType, SearchHit hit) {
        String title = String.valueOf(hit.metadata().getOrDefault("topic", sourceType));
        String content = hit.parentContent() != null && !hit.parentContent().isBlank()
                ? hit.parentContent()
                : hit.content();
        return new KnowledgeEvidenceView(
                sourceType,
                title,
                content,
                hit.score(),
                hit.score(),
                0.0,
                List.of("ES 双路召回"));
    }
}
