package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import org.springframework.stereotype.Service;

@Service
public class ActionRecommendationSkill {

    public boolean shouldCreateTicket(IntakeAnalysis analysis) {
        return analysis.isRequiresEscalation()
                || analysis.getRiskLevel().ordinal() >= 2
                || "complaint".equalsIgnoreCase(analysis.getIntent())
                || "subscription".equalsIgnoreCase(analysis.getIntent())
                || ("product_info".equalsIgnoreCase(analysis.getIntent()) && analysis.isSafetySensitive());
    }
}
