package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeEvidenceView;
import com.customer.agent.dto.ProductRetrievalAssessment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProductInfoRetrievalSkill {

    private static final Set<String> UNRELATED_TOPICS = Set.of(
            "refund", "subscription", "logistics", "complaint", "renewal", "delivery",
            "退款", "续费", "物流", "投诉", "派送"
    );

    private static final List<ProductAlias> PRODUCT_ALIASES = List.of(
            new ProductAlias("酸奶", "yogurt"),
            new ProductAlias("牛奶", "milk"),
            new ProductAlias("饼干", "biscuit"),
            new ProductAlias("面包", "bread"),
            new ProductAlias("面膜", "mask"),
            new ProductAlias("洗发水", "shampoo"),
            new ProductAlias("湿巾", "wipes"),
            new ProductAlias("软糖", "gummy"),
            new ProductAlias("奶粉", "formula"),
            new ProductAlias("防晒", "sunscreen")
    );

    public ProductRetrievalAssessment assess(String userMessage,
                                             IntakeAnalysis analysis,
                                             List<KnowledgeEvidenceView> evidences,
                                             List<KnowledgeEvidenceView> historyEvidences,
                                             String defaultSupportRoute) {
        ProductRetrievalAssessment assessment = new ProductRetrievalAssessment();
        assessment.setMatchedCategory(analysis.getProductCategory());

        if (!"product_info".equalsIgnoreCase(analysis.getDomain())) {
            List<KnowledgeEvidenceView> combined = new ArrayList<>(evidences);
            combined.addAll(historyEvidences);
            combined.sort(Comparator.comparingDouble(KnowledgeEvidenceView::score).reversed());
            assessment.setEvidences(combined);
            assessment.setGrounded(!combined.isEmpty());
            assessment.setRequiresManualReview(combined.isEmpty());
            assessment.setConfidence(combined.isEmpty() ? 0.0 : combined.get(0).score());
            assessment.setConfidenceLevel(combined.isEmpty() ? "LOW" : "HIGH");
            assessment.setSupportRoute(defaultSupportRoute);
            assessment.setConfidenceExplanation(combined.isEmpty() ? "没有可用候选。" : "非商品信息场景，直接按排序结果返回。");
            return assessment;
        }

        String normalizedMessage = normalize(userMessage);
        List<String> mentionedProducts = extractMentionedProducts(normalizedMessage);
        List<CandidateEvidence> candidates = new ArrayList<>();

        for (KnowledgeEvidenceView evidence : evidences) {
            CandidateEvidence candidate = buildCandidate(normalizedMessage, mentionedProducts, analysis, evidence);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(CandidateEvidence::finalScore).reversed());

        List<KnowledgeEvidenceView> filtered = candidates.stream()
                .map(candidate -> new KnowledgeEvidenceView(
                        candidate.evidence().sourceType(),
                        candidate.evidence().title(),
                        candidate.evidence().content(),
                        candidate.finalScore(),
                        candidate.evidence().retrievalScore(),
                        candidate.rerankScore(),
                        candidate.reasons()))
                .toList();

        assessment.setEvidences(filtered);
        if (candidates.isEmpty()) {
            assessment.setGrounded(false);
            assessment.setRequiresManualReview(analysis.isSafetySensitive());
            assessment.setConfidence(0.0);
            assessment.setConfidenceLevel("LOW");
            assessment.setSupportRoute("未命中可支撑该商品问题的知识内容，建议追问商品信息并按品类规则人工判断。");
            assessment.setConfidenceExplanation("没有命中有效商品候选。");
            return assessment;
        }

        CandidateEvidence top1 = candidates.get(0);
        CandidateEvidence top2 = candidates.size() > 1 ? candidates.get(1) : null;
        CandidateEvidence top3 = candidates.size() > 2 ? candidates.get(2) : null;

        double relativeGap12 = relativeGap(top1, top2);
        double dominance = dominance(candidates);
        long sameClusterCount = topCandidates(candidates).stream()
                .filter(candidate -> sameCluster(top1.cluster(), candidate.cluster()))
                .count();
        boolean exactWinner = top1.exactEntityMatch();
        boolean evidenceSupported = top1.intentSupported() && top1.categorySupported();
        boolean topTwoConflict = top2 != null && !sameCluster(top1.cluster(), top2.cluster());

        assessment.setConfidence(top1.finalScore());
        assessment.setLikelyMatchedProduct(top1.evidence().title());
        assessment.setLikelyMatchedGroup(clusterLabel(top1.cluster(), analysis.getProductCategory()));

        if (exactWinner && evidenceSupported && !topTwoConflict && (relativeGap12 >= 0.12 || top2 == null)) {
            assessment.setGrounded(true);
            assessment.setConfidenceLevel("HIGH");
            assessment.setRequiresManualReview(analysis.isSafetySensitive());
            assessment.setSupportRoute(defaultSupportRoute);
            assessment.setConfidenceExplanation("第一候选具备明确商品命中，且相对其他候选有明显领先。");
        } else if (!exactWinner && evidenceSupported && sameClusterCount >= 2 && !topTwoConflict && dominance >= 0.45) {
            assessment.setGrounded(false);
            assessment.setConfidenceLevel("MEDIUM");
            assessment.setRequiresManualReview(false);
            assessment.setSupportRoute(defaultSupportRoute);
            assessment.setConfidenceExplanation("候选已收敛到同一商品簇，但还没有精确到具体商品。");
        } else if ((top2 != null && relativeGap12 < 0.08) || topTwoConflict || sameClusterCount < 2) {
            assessment.setGrounded(false);
            assessment.setConfidenceLevel("AMBIGUOUS");
            assessment.setRequiresManualReview(false);
            assessment.setSupportRoute("当前存在多个相近候选商品，建议先补充具体商品名称或包装信息再确认。");
            assessment.setConfidenceExplanation("第一候选没有明显拉开其他候选，或前几名候选不属于同一商品簇。");
        } else {
            assessment.setGrounded(false);
            assessment.setConfidenceLevel("LOW");
            assessment.setRequiresManualReview(analysis.isSafetySensitive());
            assessment.setSupportRoute("命中证据不足以稳定确认商品，建议补充具体商品名称、规格或包装信息。");
            assessment.setConfidenceExplanation("候选证据不足，无法形成稳定判断。");
        }

        if (top3 != null && sameCluster(top1.cluster(), top3.cluster()) && "AMBIGUOUS".equalsIgnoreCase(assessment.getConfidenceLevel())) {
            assessment.setConfidenceLevel("MEDIUM");
            assessment.setSupportRoute(defaultSupportRoute);
            assessment.setConfidenceExplanation("虽然前两名接近，但前三名已收敛到同一商品簇，因此降为类目级中置信。");
        }

        return assessment;
    }

    private CandidateEvidence buildCandidate(String userMessage,
                                             List<String> mentionedProducts,
                                             IntakeAnalysis analysis,
                                             KnowledgeEvidenceView evidence) {
        String title = normalize(evidence.title());
        String content = normalize(evidence.content());
        String subIntent = normalize(analysis.getSubIntent());
        String category = normalize(analysis.getProductCategory());

        if ("history".equalsIgnoreCase(normalize(evidence.sourceType()))) {
            return null;
        }
        for (String unrelated : UNRELATED_TOPICS) {
            if (title.contains(unrelated) || content.contains(unrelated)) {
                return null;
            }
        }

        String cluster = detectCluster(title + "\n" + content);
        if (!mentionedProducts.isEmpty() && !matchesMentionedProduct(mentionedProducts, title, content, cluster)) {
            return null;
        }

        boolean exactEntityMatch = matchesProductName(userMessage, title, content);
        boolean intentSupported = subIntent.contains("shelf_life") && containsAny(content, "保质期", "有效期", "保存条件", "开封后建议")
                || subIntent.contains("expiry") && containsAny(content, "过期", "风险提醒", "不建议", "异常");
        boolean categorySupported =
                ("food".equals(category) && containsAny(content, "食品", "食用", "饮用", "牛奶", "酸奶", "面包", "饼干", "软糖"))
                        || ("cosmetics".equals(category) && containsAny(content, "化妆品", "护肤", "面膜", "洗发水", "防晒"))
                        || ("baby".equals(category) && containsAny(content, "母婴", "婴儿", "宝宝", "湿巾"))
                        || ("medicine".equals(category) && containsAny(content, "药品", "保健", "服用", "维生素"))
                        || ("unknown".equals(category) && !cluster.equals("unknown"));
        boolean genericDoc = title.contains("总则") || title.contains("说明");

        double rerank = 0.0;
        List<String> reasons = new ArrayList<>();
        if (exactEntityMatch) {
            rerank += 2.0;
            reasons.add("命中商品词");
        }
        if (intentSupported) {
            rerank += 1.4;
            reasons.add(subIntent.contains("expiry") ? "命中过期使用规则" : "命中保质期信息");
        }
        if (categorySupported) {
            rerank += 1.0;
            reasons.add("命中商品品类");
        }
        if (genericDoc) {
            rerank -= 0.4;
            reasons.add("通用规则降权");
        }

        double finalScore = evidence.score() + rerank;
        if (finalScore < 1.0) {
            return null;
        }

        return new CandidateEvidence(evidence, finalScore, rerank, reasons, cluster, exactEntityMatch, intentSupported, categorySupported);
    }

    private List<CandidateEvidence> topCandidates(List<CandidateEvidence> candidates) {
        return candidates.subList(0, Math.min(3, candidates.size()));
    }

    private double relativeGap(CandidateEvidence top1, CandidateEvidence top2) {
        if (top2 == null) {
            return 1.0;
        }
        double denominator = Math.max(Math.abs(top1.finalScore()), 1e-6);
        return (top1.finalScore() - top2.finalScore()) / denominator;
    }

    private double dominance(List<CandidateEvidence> candidates) {
        List<CandidateEvidence> top = topCandidates(candidates);
        double total = top.stream().mapToDouble(CandidateEvidence::finalScore).sum();
        return total == 0.0 ? 0.0 : top.get(0).finalScore() / total;
    }

    private boolean matchesMentionedProduct(List<String> mentionedProducts, String title, String content, String cluster) {
        for (String keyword : mentionedProducts) {
            if (title.contains(keyword) || content.contains(keyword) || cluster.equals(clusterOfKeyword(keyword))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesProductName(String userMessage, String title, String content) {
        for (ProductAlias alias : PRODUCT_ALIASES) {
            if (userMessage.contains(alias.keyword()) && (title.contains(alias.keyword()) || content.contains(alias.keyword()))) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractMentionedProducts(String userMessage) {
        List<String> result = new ArrayList<>();
        for (ProductAlias alias : PRODUCT_ALIASES) {
            if (userMessage.contains(alias.keyword())) {
                result.add(alias.keyword());
            }
        }
        return result;
    }

    private String detectCluster(String text) {
        for (ProductAlias alias : PRODUCT_ALIASES) {
            if (text.contains(alias.keyword())) {
                return alias.cluster();
            }
        }
        return "unknown";
    }

    private String clusterOfKeyword(String keyword) {
        for (ProductAlias alias : PRODUCT_ALIASES) {
            if (alias.keyword().equals(keyword)) {
                return alias.cluster();
            }
        }
        return "unknown";
    }

    private String clusterLabel(String cluster, String category) {
        return switch (cluster) {
            case "yogurt" -> "酸奶类商品";
            case "milk" -> "牛奶类商品";
            case "biscuit" -> "饼干类商品";
            case "bread" -> "面包类商品";
            case "mask" -> "面膜类商品";
            case "shampoo" -> "洗发水类商品";
            case "wipes" -> "湿巾类商品";
            case "gummy" -> "软糖类商品";
            case "formula" -> "奶粉类商品";
            case "sunscreen" -> "防晒类商品";
            default -> switch (category == null ? "unknown" : category) {
                case "food" -> "食品类商品";
                case "cosmetics" -> "美妆个护类商品";
                case "baby" -> "母婴类商品";
                case "medicine" -> "医药保健类商品";
                default -> "相关商品";
            };
        };
    }

    private boolean sameCluster(String cluster1, String cluster2) {
        return !cluster1.equals("unknown") && cluster1.equals(cluster2);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private record ProductAlias(String keyword, String cluster) {}

    private record CandidateEvidence(KnowledgeEvidenceView evidence,
                                     double finalScore,
                                     double rerankScore,
                                     List<String> reasons,
                                     String cluster,
                                     boolean exactEntityMatch,
                                     boolean intentSupported,
                                     boolean categorySupported) {
    }
}
