package com.customer.agent.agent;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.skill.ComplianceReviewSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QaAgent {

    private final ComplianceReviewSkill complianceReviewSkill;

    public QaDecision run(IntakeAnalysis analysis, ResolutionDraft draft, KnowledgeBundle knowledgeBundle) {
        return complianceReviewSkill.review(analysis, draft, knowledgeBundle);
    }
}
