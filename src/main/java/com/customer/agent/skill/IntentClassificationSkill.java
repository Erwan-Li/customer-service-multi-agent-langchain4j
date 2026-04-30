package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.MissingInfoAdvice;
import com.customer.agent.dto.ProductRoutingResult;
import com.customer.agent.enums.RiskLevel;
import com.customer.agent.util.JsonHelper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassificationSkill {

    private final ChatModel chatModel;
    private final JsonHelper jsonHelper;
    private final ProductIntentRoutingSkill productIntentRoutingSkill;
    private final MissingInfoCollectionSkill missingInfoCollectionSkill;

    public IntakeAnalysis analyze(String message, String history, String userProfile) {
        String prompt = """
                You are the intake analyst for a customer support workflow.
                Return JSON only.

                User profile:
                %s

                Conversation history:
                %s

                Latest user message:
                %s

                JSON schema:
                {
                  "summary": "short issue summary",
                  "intent": "primary intent",
                  "subIntent": "sub intent",
                  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                  "sentiment": "emotion description",
                  "requiresEscalation": true,
                  "missingInfo": ["missing field"],
                  "entities": {"orderId":"...", "amount":"..."}
                }
                """.formatted(userProfile, history, message);

        try {
            IntakeAnalysis analysis = jsonHelper.readValue(chatModel.chat(prompt), IntakeAnalysis.class);
            if (analysis.getRiskLevel() == null) {
                analysis.setRiskLevel(RiskLevel.LOW);
            }
            enrichWithProductRouting(message, analysis);
            return analysis;
        } catch (Exception e) {
            log.warn("Intent classification failed, fallback to rules", e);
            IntakeAnalysis fallback = buildFallback(message);
            enrichWithProductRouting(message, fallback);
            return fallback;
        }
    }

    private IntakeAnalysis buildFallback(String message) {
        IntakeAnalysis fallback = new IntakeAnalysis();
        fallback.setSummary(message);
        fallback.setIntent(message.contains("退款") ? "refund" : message.contains("续费") ? "subscription" : "general_inquiry");
        fallback.setSubIntent(message.contains("到账") ? "refund_delay" : message.contains("物流") ? "logistics_delay" : "generic");
        fallback.setRiskLevel(message.contains("投诉") || message.contains("欺诈") ? RiskLevel.HIGH : RiskLevel.LOW);
        fallback.setSentiment(message.contains("投诉") ? "angry" : "neutral");
        fallback.setRequiresEscalation(fallback.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal());
        fallback.setMissingInfo(List.of());
        fallback.setEntities(Map.of());
        return fallback;
    }

    private void enrichWithProductRouting(String message, IntakeAnalysis analysis) {
        ProductRoutingResult routingResult = productIntentRoutingSkill.route(message, analysis);
        analysis.setDomain(routingResult.getDomain());
        analysis.setProductCategory(routingResult.getProductCategory());
        analysis.setSafetySensitive(routingResult.isSafetySensitive());
        analysis.setRequiredFields(routingResult.getRequiredFields());
        if ("product_info".equalsIgnoreCase(routingResult.getDomain())) {
            analysis.setIntent("product_info");
            analysis.setSubIntent(routingResult.getSubDomain());
            if (analysis.isSafetySensitive()) {
                analysis.setRiskLevel(analysis.getRiskLevel().ordinal() < RiskLevel.MEDIUM.ordinal() ? RiskLevel.MEDIUM : analysis.getRiskLevel());
            }
        }
        MissingInfoAdvice advice = missingInfoCollectionSkill.collect(message, analysis);
        if (!advice.getMissingFields().isEmpty()) {
            analysis.setMissingInfo(advice.getMissingFields());
        }
    }
}
