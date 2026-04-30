package com.customer.agent.dto;

import com.customer.agent.enums.QaReviewStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QaDecision {
    private boolean approved;
    private QaReviewStatus reviewStatus = QaReviewStatus.AUTO_REVIEWED;
    private String finalReply;
    private String reviewSummary;
    private List<String> issues = new ArrayList<>();
    private List<String> riskFlags = new ArrayList<>();
}
