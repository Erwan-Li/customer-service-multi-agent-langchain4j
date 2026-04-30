package com.customer.agent.service;

import com.customer.agent.repository.AgentTaskRepository;
import com.customer.agent.repository.ConversationRepository;
import com.customer.agent.repository.QaAuditRepository;
import com.customer.agent.repository.TicketRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminMaintenanceService {

    private final AgentTaskRepository agentTaskRepository;
    private final QaAuditRepository qaAuditRepository;
    private final TicketRepository ticketRepository;
    private final ConversationRepository conversationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final EsHybridSearchService esHybridSearchService;
    private final KnowledgeBaseInitializer knowledgeBaseInitializer;

    @Transactional
    public void resetDemoData() {
        agentTaskRepository.deleteAllInBatch();
        qaAuditRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        conversationRepository.deleteAllInBatch();

        clearRedisByPattern("cs-agent:*");

        esHybridSearchService.resetIndex();
        knowledgeBaseInitializer.init();
    }

    private void clearRedisByPattern(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
