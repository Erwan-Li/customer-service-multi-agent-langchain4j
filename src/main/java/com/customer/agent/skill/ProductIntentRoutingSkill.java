package com.customer.agent.skill;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.ProductRoutingResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ProductIntentRoutingSkill {

    public ProductRoutingResult route(String message, IntakeAnalysis analysis) {
        String normalized = normalize(message);
        ProductRoutingResult result = new ProductRoutingResult();

        boolean productQuery = containsAny(normalized,
                "产品", "商品", "sku", "规格", "成分", "保质期", "有效期", "过期", "开封",
                "能喝", "能吃", "能用", "可以喝", "可以吃", "可以用", "食用", "使用");
        if (analysis != null && StringUtils.hasText(analysis.getIntent())) {
            String intent = normalize(analysis.getIntent());
            productQuery = productQuery
                    || intent.contains("产品")
                    || intent.contains("商品")
                    || intent.contains("product");
        }
        if (!productQuery) {
            return result;
        }

        result.setDomain("product_info");
        result.setProductCategory(detectCategory(normalized));

        boolean safetySensitive = containsAny(normalized,
                "过期", "超过保质期", "保质期过了", "变质", "异味", "发霉", "胀包",
                "能喝", "能吃", "能用", "可以喝", "可以吃", "可以用",
                "安全", "副作用", "不适", "孕妇", "婴儿", "宝宝");
        result.setSafetySensitive(safetySensitive);

        if (containsAny(normalized, "保质期", "有效期", "多久", "几天", "几个月", "几年")) {
            result.setSubDomain("shelf_life_query");
        }
        if (containsAny(normalized, "过期", "超过保质期", "保质期过了", "能喝", "能吃", "能用", "可以喝", "可以吃", "可以用")) {
            result.setSubDomain("expiry_usage");
        }
        if ("unknown".equals(result.getProductCategory()) && containsAny(normalized, "喝", "吃", "食用", "饮用")) {
            result.setProductCategory("food");
        }
        if (!StringUtils.hasText(result.getSubDomain())) {
            result.setSubDomain("general_inquiry");
        }

        List<String> requiredFields = new ArrayList<>();
        requiredFields.add("product_name");
        if ("unknown".equals(result.getProductCategory())) {
            requiredFields.add("product_category");
        }
        if (safetySensitive) {
            requiredFields.add("expiry_or_shelf_life_label");
            requiredFields.add("opened_status");
        }
        result.setRequiredFields(requiredFields);
        return result;
    }

    private String detectCategory(String normalized) {
        if (containsAny(normalized, "牛奶", "酸奶", "奶粉", "饼干", "面包", "蛋糕", "软糖", "零食", "饮料", "食品", "食用")) {
            return "food";
        }
        if (containsAny(normalized, "药", "药品", "胶囊", "片剂", "保健品", "维生素")) {
            return "medicine";
        }
        if (containsAny(normalized, "面膜", "化妆品", "护肤", "洗发水", "口红", "防晒", "乳液", "精华")) {
            return "cosmetics";
        }
        if (containsAny(normalized, "婴儿", "宝宝", "母婴", "湿巾", "纸尿裤", "奶瓶")) {
            return "baby";
        }
        if (containsAny(normalized, "家清", "洗衣液", "清洁剂", "消毒")) {
            return "household";
        }
        return "unknown";
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
