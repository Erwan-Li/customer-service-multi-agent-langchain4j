package com.customer.agent.agent;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.skill.KnowledgeRetrievalSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeAgent {

    private final KnowledgeRetrievalSkill knowledgeRetrievalSkill;

    public KnowledgeBundle run(String userId, String userMessage, IntakeAnalysis analysis) {
        return knowledgeRetrievalSkill.retrieve(userId, userMessage, analysis);
    }
}
