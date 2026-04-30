package com.customer.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MissingInfoAdvice {
    private List<String> missingFields = new ArrayList<>();
    private String followupQuestion = "";
    private boolean canProceed = true;
}
