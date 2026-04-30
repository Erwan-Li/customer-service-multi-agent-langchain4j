package com.customer.agent.agent;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.skill.IntentClassificationSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IntakeAgent {

    private final IntentClassificationSkill intentClassificationSkill;

    public IntakeAnalysis run(String message, String history, String userProfile) {
        return intentClassificationSkill.analyze(message, history, userProfile);
    }
}
