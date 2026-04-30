package com.customer.agent.skill;

import com.customer.agent.dto.EvidenceAssessment;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.KnowledgeEvidenceView;
import com.customer.agent.tool.KnowledgeSearchTools;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalSkill {

    private static final int MAX_REACT_ROUNDS = 2;

    private final KnowledgeSearchTools knowledgeSearchTools;
    private final EvidenceAssessmentSkill evidenceAssessmentSkill;

    @Value("${app.elasticsearch.top-k:5}")
    private int topK;

    public KnowledgeBundle retrieve(String userId, String userMessage, IntakeAnalysis analysis) {
        String query = buildQuery(userMessage, analysis);
        List<KnowledgeEvidenceView> evidences = new ArrayList<>();
        List<String> reactSteps = new ArrayList<>();
        EvidenceAssessment assessment = null;

        for (int round = 1; round <= MAX_REACT_ROUNDS; round++) {
            List<KnowledgeEvidenceView> faqEvidences = knowledgeSearchTools.searchFaq(query, topK);
            evidences.addAll(faqEvidences);
            reactSteps.add("Round " + round + " Action: searchFaq, query=\"" + query + "\", hits=" + faqEvidences.size());
            assessment = evidenceAssessmentSkill.assess(userMessage, analysis, rank(evidences), query, round);
            reactSteps.add(formatObservation(assessment, evidences));
            if (shouldStop(assessment)) {
                break;
            }

            List<KnowledgeEvidenceView> sopEvidences = knowledgeSearchTools.searchSop(query, topK);
            evidences.addAll(sopEvidences);
            reactSteps.add("Round " + round + " Action: searchSop, query=\"" + query + "\", hits=" + sopEvidences.size());
            assessment = evidenceAssessmentSkill.assess(userMessage, analysis, rank(evidences), query, round);
            reactSteps.add(formatObservation(assessment, evidences));
            if (shouldStop(assessment)) {
                break;
            }

            if (shouldUseHistory(assessment, analysis)) {
                List<KnowledgeEvidenceView> historyEvidences = knowledgeSearchTools.recallHistoricalTickets(userId, query, 3);
                evidences.addAll(historyEvidences);
                reactSteps.add("Round " + round + " Action: recallHistoricalTickets, query=\"" + query + "\", hits=" + historyEvidences.size());
                assessment = evidenceAssessmentSkill.assess(userMessage, analysis, rank(evidences), query, round);
                reactSteps.add(formatObservation(assessment, evidences));
                if (shouldStop(assessment)) {
                    break;
                }
            }

            String refinedQuery = assessment == null ? "" : assessment.getRefinedQuery();
            if (!StringUtils.hasText(refinedQuery) || refinedQuery.equalsIgnoreCase(query)) {
                break;
            }
            query = refinedQuery;
        }

        return buildBundle(analysis, rank(evidences), assessment, reactSteps);
    }

    private String buildQuery(String userMessage, IntakeAnalysis analysis) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(userMessage)) {
            builder.append(userMessage).append(' ');
        }
        if (StringUtils.hasText(analysis.getSummary())) {
            builder.append(analysis.getSummary()).append(' ');
        }
        if (StringUtils.hasText(analysis.getSubIntent())) {
            builder.append(analysis.getSubIntent()).append(' ');
        }
        if (StringUtils.hasText(analysis.getProductCategory())) {
            builder.append(analysis.getProductCategory());
        }
        return builder.toString().trim();
    }

    private List<KnowledgeEvidenceView> rank(List<KnowledgeEvidenceView> evidences) {
        return evidences.stream()
                .distinct()
                .sorted(Comparator.comparingDouble(KnowledgeEvidenceView::score).reversed())
                .toList();
    }

    private boolean shouldStop(EvidenceAssessment assessment) {
        if (assessment == null) {
            return false;
        }
        String nextAction = assessment.getNextAction();
        return assessment.isSufficient()
                || "STOP".equalsIgnoreCase(nextAction)
                || "ASK_USER".equalsIgnoreCase(nextAction)
                || "ESCALATE".equalsIgnoreCase(nextAction);
    }

    private boolean shouldUseHistory(EvidenceAssessment assessment, IntakeAnalysis analysis) {
        if (assessment == null || assessment.isSufficient()) {
            return false;
        }
        if ("product_info".equalsIgnoreCase(analysis.getIntent())) {
            return false;
        }
        return "RETRY_SEARCH".equalsIgnoreCase(assessment.getNextAction())
                || "LOW".equalsIgnoreCase(assessment.getConfidenceLevel())
                || "AMBIGUOUS".equalsIgnoreCase(assessment.getConfidenceLevel());
    }

    private KnowledgeBundle buildBundle(IntakeAnalysis analysis,
                                        List<KnowledgeEvidenceView> evidences,
                                        EvidenceAssessment assessment,
                                        List<String> reactSteps) {
        KnowledgeBundle bundle = new KnowledgeBundle();
        bundle.setEvidences(evidences);
        bundle.setReactSteps(reactSteps);
        bundle.setMatchedCategory(analysis.getProductCategory());
        bundle.setHistoricalInsight(buildHistoricalInsight(evidences));

        if (assessment == null) {
            bundle.setGrounded(!evidences.isEmpty());
            bundle.setRequiresManualReview(evidences.isEmpty() || analysis.isSafetySensitive());
            bundle.setConfidence(evidences.isEmpty() ? 0.0 : evidences.get(0).score());
            bundle.setConfidenceLevel(evidences.isEmpty() ? "LOW" : "MEDIUM");
            bundle.setConfidenceExplanation("KnowledgeAgent completed without an evaluator decision.");
            bundle.setSupportRoute(defaultSupportRoute(evidences));
            return bundle;
        }

        bundle.setGrounded(assessment.isSufficient());
        bundle.setRequiresManualReview(assessment.isRequiresManualReview()
                || "ESCALATE".equalsIgnoreCase(assessment.getNextAction()));
        bundle.setConfidence(confidenceScore(assessment.getConfidenceLevel(), evidences));
        bundle.setConfidenceLevel(assessment.getConfidenceLevel());
        bundle.setConfidenceExplanation(assessment.getReason());
        bundle.setSupportRoute(StringUtils.hasText(assessment.getSupportRoute())
                ? assessment.getSupportRoute()
                : defaultSupportRoute(evidences));
        bundle.setLikelyMatchedProduct(assessment.getLikelyMatchedProduct());
        bundle.setLikelyMatchedGroup(assessment.getLikelyMatchedGroup());
        return bundle;
    }

    private String buildHistoricalInsight(List<KnowledgeEvidenceView> evidences) {
        String historicalInsight = evidences.stream()
                .filter(evidence -> "HISTORY".equalsIgnoreCase(evidence.sourceType()))
                .map(KnowledgeEvidenceView::content)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return StringUtils.hasText(historicalInsight) ? historicalInsight : "暂无相似工单历史。";
    }

    private String defaultSupportRoute(List<KnowledgeEvidenceView> evidences) {
        return evidences.stream()
                .filter(evidence -> "SOP".equalsIgnoreCase(evidence.sourceType()))
                .findFirst()
                .map(KnowledgeEvidenceView::content)
                .orElse("未命中标准 SOP，建议人工判断。");
    }

    private double confidenceScore(String confidenceLevel, List<KnowledgeEvidenceView> evidences) {
        double evidenceScore = evidences.isEmpty() ? 0.0 : evidences.get(0).score();
        double levelScore = switch (confidenceLevel == null ? "LOW" : confidenceLevel.toUpperCase()) {
            case "HIGH" -> 0.9;
            case "MEDIUM" -> 0.65;
            case "AMBIGUOUS" -> 0.45;
            default -> 0.25;
        };
        return Math.max(levelScore, evidenceScore);
    }

    private String formatObservation(EvidenceAssessment assessment, List<KnowledgeEvidenceView> evidences) {
        if (assessment == null) {
            return "Observation: evaluator returned no decision, totalEvidence=" + evidences.size();
        }
        return "Observation: sufficient=" + assessment.isSufficient()
                + ", confidence=" + assessment.getConfidenceLevel()
                + ", nextAction=" + assessment.getNextAction()
                + ", totalEvidence=" + evidences.size()
                + ", reason=" + assessment.getReason();
    }
}
