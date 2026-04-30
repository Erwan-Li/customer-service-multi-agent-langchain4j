package com.customer.agent.skill;

import com.customer.agent.dto.GuardrailDecision;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.MissingInfoAdvice;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.enums.QaReviewStatus;
import com.customer.agent.util.JsonHelper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceReviewSkill {

    private final ChatModel chatModel;
    private final JsonHelper jsonHelper;
    private final ProductSafetyGuardrailSkill productSafetyGuardrailSkill;
    private final MissingInfoCollectionSkill missingInfoCollectionSkill;

    public QaDecision review(IntakeAnalysis analysis, ResolutionDraft draft, KnowledgeBundle knowledgeBundle) {
        MissingInfoAdvice missingInfoAdvice = missingInfoCollectionSkill.collect(draft.getCustomerReply(), analysis);
        GuardrailDecision guardrailDecision = productSafetyGuardrailSkill.evaluate(analysis, knowledgeBundle, missingInfoAdvice);
        if (!guardrailDecision.isAllowDirectAnswer()) {
            return buildGuardrailDecision(guardrailDecision);
        }
        if (shouldBlockUngroundedReply(draft, knowledgeBundle)) {
            return buildUngroundedDecision();
        }

        String prompt = """
                You are the QA reviewer in a customer support workflow.
                Return JSON only.

                Intake analysis:
                %s

                Knowledge:
                %s

                Draft:
                %s

                Review rules:
                - Do not promise results outside policy.
                - Use calm, empathetic language for angry users.
                - If risk is HIGH or CRITICAL, suggest manual review or escalation.
                - If knowledge is not grounded or has zero evidence, reject any factual or definitive answer and ask for clarification or manual verification instead.

                JSON schema:
                {
                  "approved": true,
                  "reviewStatus": "AUTO_REVIEWED|FLAGGED|PASSED|FAILED|MANUAL_REVIEW_REQUIRED",
                  "finalReply": "safe reply",
                  "reviewSummary": "review summary",
                  "issues": ["issue"],
                  "riskFlags": ["flag"]
                }
                """.formatted(jsonHelper.writeValue(analysis), jsonHelper.writeValue(knowledgeBundle), jsonHelper.writeValue(draft));
        try {
            QaDecision decision = jsonHelper.readValue(chatModel.chat(prompt), QaDecision.class);
            if (decision.getReviewStatus() == null) {
                decision.setReviewStatus(QaReviewStatus.AUTO_REVIEWED);
            }
            return decision;
        } catch (Exception e) {
            log.warn("Compliance review failed, fallback to conservative review", e);
            return buildConservativeDecision(analysis, draft, knowledgeBundle);
        }
    }

    private boolean shouldBlockUngroundedReply(ResolutionDraft draft, KnowledgeBundle knowledgeBundle) {
        return !knowledgeBundle.isGrounded()
                && !draft.isNeedMoreInfo()
                && containsDefinitiveLanguage(draft.getCustomerReply());
    }

    private boolean containsDefinitiveLanguage(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String normalized = reply.toLowerCase();
        return normalized.contains("两年")
                || normalized.contains("就是")
                || normalized.contains("一定")
                || normalized.contains("请您放心")
                || normalized.contains("通常情况下是可以")
                || normalized.contains("within")
                || normalized.contains(" is ")
                || normalized.contains(" are ");
    }

    private QaDecision buildGuardrailDecision(GuardrailDecision guardrailDecision) {
        QaDecision decision = new QaDecision();
        boolean informationalFollowup = guardrailDecision.isNeedMoreInfo()
                && ("MISSING_PRODUCT_INFO".equalsIgnoreCase(guardrailDecision.getRiskFlag())
                || "UNGROUNDED_PRODUCT_INFO".equalsIgnoreCase(guardrailDecision.getRiskFlag()));
        decision.setApproved(informationalFollowup);
        decision.setReviewStatus(informationalFollowup ? QaReviewStatus.AUTO_REVIEWED : QaReviewStatus.MANUAL_REVIEW_REQUIRED);
        decision.setFinalReply(guardrailDecision.getSafeReply());
        decision.setReviewSummary(informationalFollowup
                ? "当前属于信息不足场景，QA 允许直接向用户追问，不转人工复核。"
                : guardrailDecision.getReviewIssue());
        if (!informationalFollowup && !guardrailDecision.getReviewIssue().isBlank()) {
            decision.getIssues().add(guardrailDecision.getReviewIssue());
        }
        if (!informationalFollowup && !guardrailDecision.getRiskFlag().isBlank()) {
            decision.getRiskFlags().add(guardrailDecision.getRiskFlag());
        }
        return decision;
    }

    private QaDecision buildUngroundedDecision() {
        QaDecision decision = new QaDecision();
        decision.setApproved(false);
        decision.setReviewStatus(QaReviewStatus.MANUAL_REVIEW_REQUIRED);
        decision.setFinalReply("当前没有检索到可直接支撑该结论的知识依据。请补充具体商品名称、规格、链接或订单信息，我再继续为你核实。");
        decision.setReviewSummary("草稿在缺少知识依据时给出了确定性结论，已拦截并改为追问核实。");
        decision.getIssues().add("无检索命中却给出了确定性事实回答。");
        decision.getRiskFlags().add("UNGROUNDED_RESPONSE");
        return decision;
    }

    private QaDecision buildConservativeDecision(IntakeAnalysis analysis, ResolutionDraft draft, KnowledgeBundle knowledgeBundle) {
        boolean manualReviewRequired = (analysis != null && analysis.isRequiresEscalation())
                || knowledgeBundle.isRequiresManualReview();

        QaDecision fallback = new QaDecision();
        fallback.setApproved(!manualReviewRequired);
        fallback.setReviewStatus(manualReviewRequired ? QaReviewStatus.MANUAL_REVIEW_REQUIRED : QaReviewStatus.PASSED);
        fallback.setFinalReply(manualReviewRequired
                ? "当前缺少足够依据来确认具体结论，请补充商品或订单信息后我再继续核实。"
                : draft.getCustomerReply());
        fallback.setReviewSummary(manualReviewRequired ? "缺少可支撑结论的知识依据，转为保守审核结果。" : "采用保守兜底审核结果。");
        if (manualReviewRequired) {
            fallback.getIssues().add("缺少知识依据，不能直接放行确定性回复。");
            fallback.getRiskFlags().add("UNGROUNDED_RESPONSE");
        }
        return fallback;
    }
}
