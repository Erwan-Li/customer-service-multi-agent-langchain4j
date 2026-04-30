package com.customer.agent.agent;

import com.customer.agent.dto.ConversationSummary;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.skill.ConversationSummarySkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SummaryAgent {

    private final ConversationSummarySkill conversationSummarySkill;

    public ConversationSummary run(String userMessage, String finalReply, IntakeAnalysis analysis, QaDecision qaDecision) {
        return conversationSummarySkill.summarize(userMessage, finalReply, analysis, qaDecision);
    }
}
