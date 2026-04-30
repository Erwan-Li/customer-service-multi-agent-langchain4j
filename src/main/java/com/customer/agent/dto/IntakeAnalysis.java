package com.customer.agent.dto;

import com.customer.agent.enums.RiskLevel;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class IntakeAnalysis {
    private String summary;
    private String intent;
    private String subIntent;
    private RiskLevel riskLevel = RiskLevel.LOW;
    private String sentiment;
    private boolean requiresEscalation;
    private List<String> missingInfo = new ArrayList<>();
    private Map<String, Object> entities = new LinkedHashMap<>();
    private String domain = "general_service";
    private String productCategory = "unknown";
    private boolean safetySensitive;
    private List<String> requiredFields = new ArrayList<>();
}
