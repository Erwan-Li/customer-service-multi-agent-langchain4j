package com.customer.agent.skill;

import com.customer.agent.dto.ConversationSummary;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.util.JsonHelper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummarySkill {

    private final ChatModel chatModel;
    private final JsonHelper jsonHelper;

    public ConversationSummary summarize(String userMessage, String finalReply, IntakeAnalysis analysis, QaDecision qaDecision) {
        String prompt = """
                You are the summary agent for a customer support system.
                Return JSON only.

                User message:
                %s

                Final reply:
                %s

                Analysis:
                %s

                QA:
                %s

                JSON schema:
                {
                  "conversationSummary": "conversation summary",
                  "ticketSummary": "ticket summary",
                  "qualitySummary": "quality summary",
                  "tags": ["tag1", "tag2"]
                }
                """.formatted(userMessage, finalReply, jsonHelper.writeValue(analysis), jsonHelper.writeValue(qaDecision));
        try {
            return jsonHelper.readValue(chatModel.chat(prompt), ConversationSummary.class);
        } catch (Exception e) {
            log.warn("Conversation summary failed, fallback to short summary", e);
            ConversationSummary summary = new ConversationSummary();
            summary.setConversationSummary(analysis.getSummary());
            summary.setTicketSummary(analysis.getIntent() + " / " + analysis.getSubIntent());
            summary.setQualitySummary(qaDecision.getReviewSummary());
            summary.setTags(java.util.List.of(analysis.getIntent(), analysis.getSubIntent()));
            return summary;
        }
    }
}
