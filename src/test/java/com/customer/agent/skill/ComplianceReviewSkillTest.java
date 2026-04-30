package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.enums.QaReviewStatus;
import com.customer.agent.util.JsonHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ComplianceReviewSkillTest {

    private final ComplianceReviewSkill skill = new ComplianceReviewSkill(
            mock(ChatModel.class),
            new JsonHelper(new ObjectMapper()),
            new ProductSafetyGuardrailSkill(),
            new MissingInfoCollectionSkill()
    );

    @Test
    void shouldBlockDefinitiveReplyWhenKnowledgeIsUngrounded() {
        IntakeAnalysis analysis = new IntakeAnalysis();
        analysis.setDomain("product_info");
        analysis.setProductCategory("food");
        analysis.setSafetySensitive(true);

        ResolutionDraft draft = new ResolutionDraft();
        draft.setCustomerReply("您好，我们的产品保质期为两年，请您放心使用。");
        draft.setNeedMoreInfo(false);

        KnowledgeBundle knowledgeBundle = new KnowledgeBundle();
        knowledgeBundle.setGrounded(false);
        knowledgeBundle.setRequiresManualReview(true);

        QaDecision decision = skill.review(analysis, draft, knowledgeBundle);

        assertFalse(decision.isApproved());
        assertEquals(QaReviewStatus.MANUAL_REVIEW_REQUIRED, decision.getReviewStatus());
        assertTrue(decision.getRiskFlags().contains("PRODUCT_SAFETY_GUARDRAIL")
                || decision.getRiskFlags().contains("MISSING_PRODUCT_INFO"));
        assertTrue(decision.getFinalReply() != null && !decision.getFinalReply().isBlank());
    }
}
