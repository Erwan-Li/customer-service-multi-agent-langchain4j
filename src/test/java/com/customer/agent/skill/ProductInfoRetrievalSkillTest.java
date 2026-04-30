package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeEvidenceView;
import com.customer.agent.dto.ProductRetrievalAssessment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductInfoRetrievalSkillTest {

    private final ProductInfoRetrievalSkill skill = new ProductInfoRetrievalSkill();

    @Test
    void shouldRejectUnrelatedFaqsForProductSafetyQuery() {
        IntakeAnalysis analysis = new IntakeAnalysis();
        analysis.setDomain("product_info");
        analysis.setSubIntent("expiry_usage");
        analysis.setProductCategory("food");
        analysis.setSafetySensitive(true);

        List<KnowledgeEvidenceView> evidences = List.of(
                new KnowledgeEvidenceView("FAQ", "退款到账延迟", "银行卡退款通常 1 到 7 个工作日到账。", 1.5, 1.5, 0.0, List.of()),
                new KnowledgeEvidenceView("FAQ", "投诉升级", "投诉需要转专人处理。", 1.4, 1.4, 0.0, List.of()),
                new KnowledgeEvidenceView("FAQ", "General", "# Customer Service Knowledge Base", 1.6, 1.6, 0.0, List.of())
        );

        ProductRetrievalAssessment assessment = skill.assess(
                "酸奶过期了还能喝吗",
                analysis,
                evidences,
                List.<KnowledgeEvidenceView>of(),
                "未命中标准 SOP，建议人工判断。"
        );

        assertFalse(assessment.isGrounded());
        assertTrue(assessment.getEvidences().isEmpty());
        assertTrue(assessment.isRequiresManualReview());
    }
}
