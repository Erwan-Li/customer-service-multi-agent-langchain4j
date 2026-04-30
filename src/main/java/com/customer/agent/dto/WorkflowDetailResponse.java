package com.customer.agent.dto;

import com.customer.agent.entity.AgentTask;
import com.customer.agent.entity.Conversation;
import com.customer.agent.entity.QaAudit;
import com.customer.agent.entity.Ticket;

import java.util.List;

public record WorkflowDetailResponse(
        Conversation conversation,
        IntakeAnalysis analysis,
        List<KnowledgeEvidenceView> evidences,
        String finalReply,
        String internalSuggestion,
        Ticket ticket,
        QaAudit qaAudit,
        List<AgentTask> agentTasks,
        WorkflowDecision decision
) {
}
