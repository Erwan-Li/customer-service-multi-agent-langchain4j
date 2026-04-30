package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.MissingInfoAdvice;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MissingInfoCollectionSkill {

    public MissingInfoAdvice collect(String userMessage, IntakeAnalysis analysis) {
        MissingInfoAdvice advice = new MissingInfoAdvice();
        if (analysis == null || !"product_info".equalsIgnoreCase(analysis.getDomain())) {
            advice.setCanProceed(true);
            return advice;
        }

        String normalized = normalize(userMessage);
        List<String> missingFields = new ArrayList<>();

        if (!hasSpecificProductName(normalized, analysis)) {
            missingFields.add("product_name");
        }
        if (!StringUtils.hasText(analysis.getProductCategory()) || "unknown".equalsIgnoreCase(analysis.getProductCategory())) {
            missingFields.add("product_category");
        }

        if (analysis.isSafetySensitive()) {
            if (!containsAny(normalized, "保质期", "有效期", "到期", "过期", "生产日期")) {
                missingFields.add("expiry_or_shelf_life_label");
            }
            if (!containsAny(normalized, "开封", "未开封")) {
                missingFields.add("opened_status");
            }
        }

        advice.setMissingFields(missingFields);
        advice.setCanProceed(missingFields.isEmpty());
        if (!missingFields.isEmpty()) {
            advice.setFollowupQuestion(buildFollowupQuestion(analysis));
        }
        return advice;
    }

    private boolean hasSpecificProductName(String normalized, IntakeAnalysis analysis) {
        if (containsAny(normalized, "酸奶", "牛奶", "饼干", "面包", "面膜", "洗发水", "湿巾", "软糖", "奶粉")) {
            return true;
        }
        if (containsAny(normalized, "这个", "那个", "产品", "商品") && normalized.length() <= 14) {
            return false;
        }
        if (StringUtils.hasText(analysis.getProductCategory()) && !"unknown".equalsIgnoreCase(analysis.getProductCategory())) {
            return normalized.length() >= 6;
        }
        return normalized.length() >= 8;
    }

    private String buildFollowupQuestion(IntakeAnalysis analysis) {
        if (analysis.isSafetySensitive()) {
            return "这类问题涉及商品安全判断。请补充具体商品名称、是否已开封、过期多久以及是否有异味、胀包或结块等异常情况，我再继续帮你判断。";
        }
        return "请补充具体商品名称、品类或规格信息，我再继续帮你查询。";
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
}
