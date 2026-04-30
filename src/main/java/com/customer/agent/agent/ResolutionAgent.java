package com.customer.agent.agent;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.skill.ResponseDraftingSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResolutionAgent {

    private final ResponseDraftingSkill responseDraftingSkill;

    public ResolutionDraft run(String userMessage, IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle) {
        return responseDraftingSkill.draft(userMessage, analysis, knowledgeBundle);
    }
}
