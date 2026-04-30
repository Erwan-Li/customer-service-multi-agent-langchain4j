package com.customer.agent.skill;

import com.customer.agent.dto.EvidenceAssessment;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeEvidenceView;
import com.customer.agent.util.JsonHelper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceAssessmentSkill {

    private final ChatModel chatModel;
    private final JsonHelper jsonHelper;

    public EvidenceAssessment assess(String userMessage,
                                     IntakeAnalysis analysis,
                                     List<KnowledgeEvidenceView> evidences,
                                     String currentQuery,
                                     int round) {
        String prompt = """
                You are the evidence evaluator inside a customer-service KnowledgeAgent.
                Return JSON only.

                Decide whether the retrieved evidence is enough to support the next customer reply.
                Do not require perfect evidence, but reject unsupported factual claims.
                Prefer ASK_USER when the missing information can only be provided by the customer.
                Prefer RETRY_SEARCH when a better search query could plausibly find support.
                Prefer ESCALATE for high-risk or safety-sensitive cases with weak evidence.

                User message:
                %s

                Intake analysis:
                %s

                Current query:
                %s

                ReAct round:
                %d

                Retrieved evidence:
                %s

                JSON schema:
                {
                  "sufficient": true,
                  "confidenceLevel": "HIGH|MEDIUM|LOW|AMBIGUOUS",
                  "reason": "why the evidence is or is not enough",
                  "missingFields": ["field"],
                  "nextAction": "STOP|RETRY_SEARCH|ASK_USER|ESCALATE",
                  "refinedQuery": "better query when nextAction is RETRY_SEARCH",
                  "supportRoute": "internal handling route or SOP summary",
                  "requiresManualReview": false,
                  "likelyMatchedProduct": "optional",
                  "likelyMatchedGroup": "optional"
                }
                """.formatted(
                userMessage,
                jsonHelper.writeValue(analysis),
                currentQuery,
                round,
                jsonHelper.writeValue(evidences));

        try {
            EvidenceAssessment assessment = jsonHelper.readValue(chatModel.chat(prompt), EvidenceAssessment.class);
            normalize(assessment);
            return assessment;
        } catch (Exception e) {
            log.warn("Evidence assessment failed, using conservative fallback", e);
            return fallbackAssessment(analysis, evidences, currentQuery);
        }
    }

    private void normalize(EvidenceAssessment assessment) {
        if (!StringUtils.hasText(assessment.getConfidenceLevel())) {
            assessment.setConfidenceLevel(assessment.isSufficient() ? "MEDIUM" : "LOW");
        }
        if (!StringUtils.hasText(assessment.getNextAction())) {
            assessment.setNextAction(assessment.isSufficient() ? "STOP" : "RETRY_SEARCH");
        }
        if (assessment.isSufficient() && !"ESCALATE".equalsIgnoreCase(assessment.getNextAction())) {
            assessment.setNextAction("STOP");
        }
    }

    private EvidenceAssessment fallbackAssessment(IntakeAnalysis analysis,
                                                  List<KnowledgeEvidenceView> evidences,
                                                  String currentQuery) {
        EvidenceAssessment fallback = new EvidenceAssessment();
        if (evidences == null || evidences.isEmpty()) {
            fallback.setSufficient(false);
            fallback.setConfidenceLevel("LOW");
            fallback.setReason("No retrieved evidence is available.");
            fallback.setNextAction(StringUtils.hasText(currentQuery) ? "ASK_USER" : "RETRY_SEARCH");
            fallback.setRefinedQuery(currentQuery);
            fallback.setRequiresManualReview(analysis != null && analysis.isSafetySensitive());
            return fallback;
        }

        KnowledgeEvidenceView top = evidences.get(0);
        fallback.setSufficient(top.score() >= 1.0);
        fallback.setConfidenceLevel(fallback.isSufficient() ? "MEDIUM" : "LOW");
        fallback.setReason("Fallback assessment based on available evidence score because the LLM evaluator failed.");
        fallback.setNextAction(fallback.isSufficient() ? "STOP" : "ASK_USER");
        fallback.setSupportRoute(top.content());
        fallback.setRequiresManualReview(analysis != null && analysis.isSafetySensitive() && !fallback.isSufficient());
        return fallback;
    }
}
