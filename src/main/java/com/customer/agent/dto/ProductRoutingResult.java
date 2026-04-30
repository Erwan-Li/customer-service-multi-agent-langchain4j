package com.customer.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductRoutingResult {
    private String domain = "general_service";
    private String subDomain = "general_inquiry";
    private String productCategory = "unknown";
    private boolean safetySensitive;
    private List<String> requiredFields = new ArrayList<>();
}
