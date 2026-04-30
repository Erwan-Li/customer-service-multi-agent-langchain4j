package com.customer.agent.skill;

import com.customer.agent.dto.GuardrailDecision;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.MissingInfoAdvice;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.util.JsonHelper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseDraftingSkill {

    private final ChatModel chatModel;
    private final JsonHelper jsonHelper;
    private final ActionRecommendationSkill actionRecommendationSkill;
    private final ProductSafetyGuardrailSkill productSafetyGuardrailSkill;
    private final MissingInfoCollectionSkill missingInfoCollectionSkill;

    public ResolutionDraft draft(String userMessage, IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle) {
        MissingInfoAdvice missingInfoAdvice = missingInfoCollectionSkill.collect(userMessage, analysis);
        GuardrailDecision guardrailDecision = productSafetyGuardrailSkill.evaluate(analysis, knowledgeBundle, missingInfoAdvice);
        if (!guardrailDecision.isAllowDirectAnswer()) {
            return buildGuardrailFallback(analysis, knowledgeBundle, guardrailDecision, missingInfoAdvice);
        }
        if ("AMBIGUOUS".equalsIgnoreCase(knowledgeBundle.getConfidenceLevel())) {
            return buildAmbiguousFallback(analysis, knowledgeBundle);
        }
        if ("MEDIUM".equalsIgnoreCase(knowledgeBundle.getConfidenceLevel())) {
            return buildLikelyMatchFallback(analysis, knowledgeBundle);
        }
        if (!knowledgeBundle.isGrounded()) {
            return buildUngroundedFallback(analysis, knowledgeBundle);
        }

        String prompt = """
                You are the resolution agent in a customer support workflow.
                Return JSON only.

                Hard rules:
                - Only answer factual questions when the provided knowledge contains supporting evidence.
                - If the knowledge is empty or not grounded, do not guess, do not invent policy, and ask the user for the missing product or order details.
                - Do not provide medical, food safety, or high-risk product safety judgments beyond the supplied policy.

                User message:
                %s

                Intake analysis:
                %s

                Knowledge:
                %s

                JSON schema:
                {
                  "customerReply": "reply to the customer",
                  "internalSuggestion": "internal handling suggestion",
                  "needMoreInfo": false,
                  "followupQuestion": "if need more info",
                  "createTicket": false,
                  "replyStrategy": "DIRECT"
                }
                """.formatted(userMessage, jsonHelper.writeValue(analysis), jsonHelper.writeValue(knowledgeBundle));
        try {
            ResolutionDraft draft = jsonHelper.readValue(chatModel.chat(prompt), ResolutionDraft.class);
            draft.setCreateTicket(draft.isCreateTicket() || actionRecommendationSkill.shouldCreateTicket(analysis));
            if (draft.getReplyStrategy() == null || draft.getReplyStrategy().isBlank()) {
                draft.setReplyStrategy("DIRECT");
            }
            return draft;
        } catch (Exception e) {
            log.warn("Draft generation failed, fallback to template", e);
            ResolutionDraft fallback = new ResolutionDraft();
            fallback.setCustomerReply("我们已经收到你的问题，会先根据现有信息继续核查；如果你方便，也可以补充具体商品名称、规格或订单信息。");
            fallback.setInternalSuggestion(knowledgeBundle.getSupportRoute());
            fallback.setNeedMoreInfo(!analysis.getMissingInfo().isEmpty());
            fallback.setFollowupQuestion(fallback.isNeedMoreInfo() ? "请补充具体商品名称、规格或订单信息，方便继续核实。" : "");
            fallback.setCreateTicket(actionRecommendationSkill.shouldCreateTicket(analysis));
            fallback.setReplyStrategy("FALLBACK");
            return fallback;
        }
    }

    private ResolutionDraft buildGuardrailFallback(IntakeAnalysis analysis,
                                                   KnowledgeBundle knowledgeBundle,
                                                   GuardrailDecision guardrailDecision,
                                                   MissingInfoAdvice missingInfoAdvice) {
        ResolutionDraft fallback = new ResolutionDraft();
        fallback.setCustomerReply(guardrailDecision.getSafeReply());
        fallback.setInternalSuggestion(knowledgeBundle.getSupportRoute());
        fallback.setNeedMoreInfo(guardrailDecision.isNeedMoreInfo());
        fallback.setFollowupQuestion(fallback.isNeedMoreInfo() && missingInfoAdvice != null
                ? missingInfoAdvice.getFollowupQuestion()
                : "");
        fallback.setCreateTicket(guardrailDecision.isCreateTicket() || actionRecommendationSkill.shouldCreateTicket(analysis));
        fallback.setReplyStrategy("GUARDRAIL");
        return fallback;
    }

    private ResolutionDraft buildLikelyMatchFallback(IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle) {
        ResolutionDraft fallback = new ResolutionDraft();
        String likelyGroup = knowledgeBundle.getLikelyMatchedGroup();
        String firstEvidenceContent = knowledgeBundle.getEvidences().isEmpty() ? "" : knowledgeBundle.getEvidences().get(0).content();
        fallback.setCustomerReply(String.format(
                "我理解你大概率在问%s。%s如果你方便提供具体商品全名或包装信息，我可以继续帮你确认得更准确。",
                likelyGroup == null || likelyGroup.isBlank() ? "相关商品" : likelyGroup,
                summarizeForCustomer(firstEvidenceContent)
        ));
        fallback.setInternalSuggestion("当前属于类目级中置信命中，候选已收敛到同一商品簇，但还不能确认具体商品。");
        fallback.setNeedMoreInfo(true);
        fallback.setFollowupQuestion("如果方便，请补充具体商品全名或包装信息，我可以继续帮你精确确认。");
        fallback.setCreateTicket(actionRecommendationSkill.shouldCreateTicket(analysis));
        fallback.setReplyStrategy("LIKELY_MATCH");
        return fallback;
    }

    private ResolutionDraft buildAmbiguousFallback(IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle) {
        ResolutionDraft fallback = new ResolutionDraft();
        fallback.setCustomerReply("我目前匹配到多个相近商品候选，不同商品的保质期或处理规则可能不同。请补充具体商品名称、规格或包装信息，我再帮你准确确认。");
        fallback.setInternalSuggestion("当前候选之间竞争激烈，前几名没有形成稳定领先，不适合给出具体商品级答案。");
        fallback.setNeedMoreInfo(true);
        fallback.setFollowupQuestion("请补充具体商品名称、规格或包装信息，我再帮你准确确认。");
        fallback.setCreateTicket(actionRecommendationSkill.shouldCreateTicket(analysis));
        fallback.setReplyStrategy("AMBIGUOUS");
        return fallback;
    }

    private ResolutionDraft buildUngroundedFallback(IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle) {
        ResolutionDraft fallback = new ResolutionDraft();
        fallback.setCustomerReply("当前没有检索到可以直接支撑该结论的知识内容。为了避免给出错误信息，请补充具体商品名称、规格或链接，我再帮你继续核实。");
        fallback.setInternalSuggestion(knowledgeBundle.getSupportRoute());
        fallback.setNeedMoreInfo(true);
        fallback.setFollowupQuestion("请提供具体商品名称、规格、链接或订单信息，方便继续核实。");
        fallback.setCreateTicket(actionRecommendationSkill.shouldCreateTicket(analysis));
        fallback.setReplyStrategy("NO_CONFIDENCE");
        return fallback;
    }

    private String summarizeForCustomer(String evidenceContent) {
        if (evidenceContent == null || evidenceContent.isBlank()) {
            return "目前可以先给你一个大致参考。";
        }
        int start = evidenceContent.indexOf("标准保质期");
        if (start >= 0) {
            int end = Math.min(evidenceContent.length(), start + 30);
            return evidenceContent.substring(start, end).replace("\n", "，") + "。";
        }
        return "目前可以先给你一个大致参考。";
    }
}
