package com.customer.agent.service;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.dto.WorkflowDecision;
import com.customer.agent.dto.WorkflowDetailResponse;
import com.customer.agent.entity.AgentTask;
import com.customer.agent.entity.Conversation;
import com.customer.agent.entity.QaAudit;
import com.customer.agent.entity.Ticket;
import com.customer.agent.repository.AgentTaskRepository;
import com.customer.agent.repository.ConversationRepository;
import com.customer.agent.repository.QaAuditRepository;
import com.customer.agent.repository.TicketRepository;
import com.customer.agent.util.JsonHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowDetailQueryService {

    private final ConversationRepository conversationRepository;
    private final TicketRepository ticketRepository;
    private final QaAuditRepository qaAuditRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final ConversationWorkflowService conversationWorkflowService;
    private final JsonHelper jsonHelper;

    public WorkflowDetailResponse detail(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        Ticket ticket = ticketRepository.findTop1ByConversationIdOrderByUpdatedAtDesc(conversationId).orElse(null);
        QaAudit qaAudit = qaAuditRepository.findTop1ByConversationIdOrderByUpdatedAtDesc(conversationId).orElse(null);
        List<AgentTask> agentTasks = agentTaskRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        IntakeAnalysis analysis = new IntakeAnalysis();
        KnowledgeBundle knowledgeBundle = new KnowledgeBundle();
        ResolutionDraft draft = new ResolutionDraft();
        QaDecision qaDecision = new QaDecision();

        for (AgentTask task : agentTasks) {
            switch (task.getAgentType()) {
                case "IntakeAgent" -> analysis = safeRead(task.getOutputSummary(), IntakeAnalysis.class, new IntakeAnalysis());
                case "KnowledgeAgent" -> knowledgeBundle = safeRead(task.getOutputSummary(), KnowledgeBundle.class, new KnowledgeBundle());
                case "ResolutionAgent" -> draft = safeRead(task.getOutputSummary(), ResolutionDraft.class, new ResolutionDraft());
                case "QAAgent" -> qaDecision = safeRead(task.getOutputSummary(), QaDecision.class, new QaDecision());
                default -> {
                }
            }
        }

        WorkflowDecision decision = conversationWorkflowService.decideWorkflow(
                conversation.getLatestUserMessage(),
                analysis,
                draft,
                qaDecision);

        return new WorkflowDetailResponse(
                conversation,
                analysis,
                knowledgeBundle.getEvidences(),
                conversation.getLatestAssistantReply(),
                draft.getInternalSuggestion(),
                ticket,
                qaAudit,
                agentTasks,
                decision);
    }

    private <T> T safeRead(String text, Class<T> clazz, T fallback) {
        try {
            return jsonHelper.readValue(text, clazz);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
