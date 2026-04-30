package com.customer.agent.skill;

import com.customer.agent.dto.GuardrailDecision;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.MissingInfoAdvice;
import org.springframework.stereotype.Service;

@Service
public class ProductSafetyGuardrailSkill {

    public GuardrailDecision evaluate(IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle, MissingInfoAdvice missingInfoAdvice) {
        GuardrailDecision decision = new GuardrailDecision();
        if (analysis == null || !"product_info".equalsIgnoreCase(analysis.getDomain())) {
            return decision;
        }

        String confidenceLevel = knowledgeBundle == null ? "LOW" : knowledgeBundle.getConfidenceLevel();

        if (analysis.isSafetySensitive()) {
            decision.setAllowDirectAnswer(false);
            decision.setNeedMoreInfo(false);
            decision.setRequiresManualReview(true);
            decision.setCreateTicket(true);
            decision.setSafeReply(buildSafetyReply(knowledgeBundle));
            decision.setReviewIssue("涉及商品安全或过期使用风险，优先给出保守结论。");
            decision.setRiskFlag("PRODUCT_SAFETY_GUARDRAIL");
            return decision;
        }

        boolean missingInfo = missingInfoAdvice != null && !missingInfoAdvice.isCanProceed();

        if ("HIGH".equalsIgnoreCase(confidenceLevel) && knowledgeBundle.isGrounded()) {
            return decision;
        }
        if ("MEDIUM".equalsIgnoreCase(confidenceLevel)) {
            return decision;
        }
        if (missingInfo) {
            decision.setAllowDirectAnswer(false);
            decision.setNeedMoreInfo(true);
            decision.setSafeReply(missingInfoAdvice.getFollowupQuestion());
            decision.setReviewIssue("商品信息不足，不能直接回答。");
            decision.setRiskFlag("MISSING_PRODUCT_INFO");
            return decision;
        }
        if (!knowledgeBundle.isGrounded()) {
            decision.setAllowDirectAnswer(false);
            decision.setNeedMoreInfo(true);
            decision.setSafeReply("当前没有检索到可直接支撑该商品信息的知识依据，请补充具体商品名称、品类或规格，我再继续帮你核实。");
            decision.setReviewIssue("缺少可支撑结论的商品知识依据。");
            decision.setRiskFlag("UNGROUNDED_PRODUCT_INFO");
        }
        return decision;
    }

    private String buildSafetyReply(KnowledgeBundle knowledgeBundle) {
        if (knowledgeBundle != null
                && "HIGH".equalsIgnoreCase(knowledgeBundle.getConfidenceLevel())
                && knowledgeBundle.isGrounded()
                && knowledgeBundle.getLikelyMatchedProduct() != null
                && !knowledgeBundle.getLikelyMatchedProduct().isBlank()) {
            return "一般不建议继续食用或使用这类已过期商品。为了安全起见，请先不要继续饮用或使用。"
                    + " 如果你怀疑商品存在质量问题，或者已经食用后出现不适，可以继续提供商品名称、订单信息和异常情况，我们会进一步跟进。";
        }
        return "这类问题涉及商品安全判断。为了安全起见，不建议继续食用或使用已过期商品。"
                + " 如果你怀疑商品存在质量问题，或者已经食用后出现不适，可以继续提供商品名称、订单信息和异常情况，我们会进一步跟进。";
    }
}
