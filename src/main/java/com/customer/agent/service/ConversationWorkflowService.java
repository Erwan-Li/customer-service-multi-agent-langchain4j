package com.customer.agent.service;

import com.customer.agent.agent.IntakeAgent;
import com.customer.agent.agent.KnowledgeAgent;
import com.customer.agent.agent.QaAgent;
import com.customer.agent.agent.ResolutionAgent;
import com.customer.agent.agent.SummaryAgent;
import com.customer.agent.dto.ConversationEvent;
import com.customer.agent.dto.ConversationSummary;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.MemorySummaryTask;
import com.customer.agent.dto.MessageRequest;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.dto.WorkflowDecision;
import com.customer.agent.dto.WorkflowResponse;
import com.customer.agent.entity.AgentTask;
import com.customer.agent.entity.Conversation;
import com.customer.agent.entity.QaAudit;
import com.customer.agent.entity.Ticket;
import com.customer.agent.enums.ConversationStatus;
import com.customer.agent.enums.PriorityLevel;
import com.customer.agent.enums.QaReviewStatus;
import com.customer.agent.enums.RiskLevel;
import com.customer.agent.enums.TaskStatus;
import com.customer.agent.enums.TicketStatus;
import com.customer.agent.repository.AgentTaskRepository;
import com.customer.agent.repository.ConversationRepository;
import com.customer.agent.repository.QaAuditRepository;
import com.customer.agent.repository.TicketRepository;
import com.customer.agent.util.JsonHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConversationWorkflowService {

    private final ConversationRepository conversationRepository;
    private final TicketRepository ticketRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final QaAuditRepository qaAuditRepository;
    private final ConversationEventStore conversationEventStore;
    private final LongTermMemoryService longTermMemoryService;
    private final MemorySummaryProducer memorySummaryProducer;
    private final IntakeAgent intakeAgent;
    private final KnowledgeAgent knowledgeAgent;
    private final ResolutionAgent resolutionAgent;
    private final QaAgent qaAgent;
    private final SummaryAgent summaryAgent;
    private final JsonHelper jsonHelper;

    @Transactional
    public WorkflowResponse process(MessageRequest request) {
        Conversation conversation = initConversation(request);
        appendUserEvent(conversation.getId(), request.getMessage());
        String effectiveMessage = enrichMessageWithContext(conversation, request.getMessage());

        String history = buildHistory(conversation.getId());
        String userProfile = longTermMemoryService.loadUserProfile(request.getUserId());

        conversation.setStatus(ConversationStatus.UNDERSTANDING);
        conversationRepository.save(conversation);
        IntakeAnalysis analysis = intakeAgent.run(effectiveMessage, history, userProfile);
        updateConversationFromAnalysis(conversation, request.getMessage(), analysis);
        saveTask(conversation.getId(), "IntakeAgent", "识别用户意图", jsonHelper.writeValue(request), jsonHelper.writeValue(analysis));

        conversation.setStatus(ConversationStatus.RETRIEVING);
        conversationRepository.save(conversation);
        KnowledgeBundle knowledgeBundle = knowledgeAgent.run(request.getUserId(), effectiveMessage, analysis);
        reconcileMissingInfoWithKnowledge(analysis, knowledgeBundle);
        saveTask(conversation.getId(), "KnowledgeAgent", "检索知识、SOP 与历史记录", analysis.getSummary(), jsonHelper.writeValue(knowledgeBundle));

        conversation.setStatus(ConversationStatus.DRAFTING);
        conversationRepository.save(conversation);
        ResolutionDraft draft = resolutionAgent.run(effectiveMessage, analysis, knowledgeBundle);
        saveTask(conversation.getId(), "ResolutionAgent", "生成回复与处理建议", request.getMessage(), jsonHelper.writeValue(draft));

        conversation.setStatus(ConversationStatus.REVIEWING);
        conversationRepository.save(conversation);
        QaDecision qaDecision = qaAgent.run(analysis, draft, knowledgeBundle);
        QaAudit qaAudit = saveQaAudit(conversation.getId(), qaDecision);
        saveTask(conversation.getId(), "QAAgent", "审核合规与质量", draft.getCustomerReply(), jsonHelper.writeValue(qaDecision));

        String finalReply = StringUtils.hasText(qaDecision.getFinalReply())
                ? qaDecision.getFinalReply()
                : draft.getCustomerReply();
        conversation.setLatestAssistantReply(finalReply);

        WorkflowDecision decision = decideWorkflow(effectiveMessage, analysis, draft, qaDecision);
        Ticket ticket = null;
        if (decision.escalated()) {
            conversation.setStatus(ConversationStatus.ESCALATED);
            if (decision.createTicket()) {
                ticket = upsertTicket(conversation, analysis, draft);
            }
        } else if (decision.needsMoreInfo()) {
            conversation.setStatus(ConversationStatus.NEEDS_INFO);
        } else {
            conversation.setStatus(ConversationStatus.READY_TO_SEND);
        }

        ConversationSummary summary = summaryAgent.run(effectiveMessage, finalReply, analysis, qaDecision);
        conversation.setSummary(summary.getConversationSummary());
        conversation.setTagsJson(jsonHelper.writeValue(summary.getTags()));
        conversationRepository.save(conversation);
        saveTask(conversation.getId(), "SummaryAgent", "生成摘要与标签", finalReply, jsonHelper.writeValue(summary));

        appendAssistantEvent(conversation.getId(), finalReply);
        longTermMemoryService.rememberQaFeedback(conversation.getId(), conversation.getUserId(), qaDecision.getReviewSummary(), qaDecision.getIssues());
        MemorySummaryTask memoryTask = new MemorySummaryTask();
        memoryTask.setConversationId(conversation.getId());
        memoryTask.setUserId(conversation.getUserId());
        memorySummaryProducer.send(memoryTask);

        return new WorkflowResponse(
                conversation,
                analysis,
                knowledgeBundle.getEvidences(),
                finalReply,
                draft.getInternalSuggestion(),
                ticket,
                qaAudit,
                agentTaskRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()),
                decision);
    }

    private String enrichMessageWithContext(Conversation conversation, String currentMessage) {
        if (!StringUtils.hasText(currentMessage) || conversation == null) {
            return currentMessage;
        }
        boolean supplementTurn = conversation.getStatus() == ConversationStatus.NEEDS_INFO
                || value(conversation.getLatestAssistantReply()).contains("请补充");
        if (!supplementTurn || currentMessage.length() >= 8) {
            return currentMessage;
        }
        String previousMessage = value(conversation.getLatestUserMessage());
        if (!StringUtils.hasText(previousMessage)) {
            return currentMessage;
        }
        return previousMessage + "\n用户补充信息：" + currentMessage;
    }

    private Conversation initConversation(MessageRequest request) {
        if (StringUtils.hasText(request.getConversationId())) {
            return conversationRepository.findById(request.getConversationId())
                    .map(existing -> {
                        existing.setChannel(request.getChannel());
                        existing.setUserId(request.getUserId());
                        return existing;
                    })
                    .orElseGet(() -> buildNewConversation(request));
        }
        return buildNewConversation(request);
    }

    private Conversation buildNewConversation(MessageRequest request) {
        Conversation conversation = new Conversation();
        conversation.setUserId(request.getUserId());
        conversation.setChannel(request.getChannel());
        conversation.setStatus(ConversationStatus.NEW);
        return conversationRepository.save(conversation);
    }

    private void reconcileMissingInfoWithKnowledge(IntakeAnalysis analysis, KnowledgeBundle knowledgeBundle) {
        if (analysis == null || knowledgeBundle == null || analysis.getMissingInfo() == null || analysis.getMissingInfo().isEmpty()) {
            return;
        }
        boolean highConfidenceGrounded = knowledgeBundle.isGrounded()
                && "HIGH".equalsIgnoreCase(knowledgeBundle.getConfidenceLevel());
        boolean strongSingleCandidate = knowledgeBundle.getEvidences() != null
                && !knowledgeBundle.getEvidences().isEmpty()
                && (knowledgeBundle.getEvidences().size() == 1
                || knowledgeBundle.getEvidences().get(0).matchReasons().contains("命中商品词"));
        if (highConfidenceGrounded || strongSingleCandidate) {
            analysis.getMissingInfo().remove("product_name");
            analysis.getMissingInfo().remove("product_category");
        }
    }

    private void updateConversationFromAnalysis(Conversation conversation, String message, IntakeAnalysis analysis) {
        conversation.setLatestUserMessage(message);
        conversation.setIntent(analysis.getIntent());
        conversation.setSubIntent(analysis.getSubIntent());
        conversation.setRiskLevel(analysis.getRiskLevel());
        conversation.setSummary(analysis.getSummary());
        conversationRepository.save(conversation);
    }

    private Ticket upsertTicket(Conversation conversation, IntakeAnalysis analysis, ResolutionDraft draft) {
        Ticket ticket = ticketRepository.findTop1ByConversationIdOrderByUpdatedAtDesc(conversation.getId())
                .orElseGet(Ticket::new);
        ticket.setConversationId(conversation.getId());
        ticket.setUserId(conversation.getUserId());
        ticket.setCategory(analysis.getIntent() == null ? "general" : analysis.getIntent());
        ticket.setPriority(analysis.getRiskLevel().ordinal() >= 2 ? PriorityLevel.HIGH : PriorityLevel.MEDIUM);
        ticket.setStatus(TicketStatus.QUEUED);
        ticket.setSummary(draft.getInternalSuggestion());
        return ticketRepository.save(ticket);
    }

    WorkflowDecision decideWorkflow(String userMessage, IntakeAnalysis analysis, ResolutionDraft draft, QaDecision qaDecision) {
        boolean manualReviewRequired = qaDecision.getReviewStatus() == QaReviewStatus.FAILED
                || qaDecision.getReviewStatus() == QaReviewStatus.MANUAL_REVIEW_REQUIRED
                || !qaDecision.isApproved();
        boolean needsMoreInfo = draft.isNeedMoreInfo()
                || (analysis.getMissingInfo() != null && !analysis.getMissingInfo().isEmpty());
        boolean highRiskEscalation = analysis.isRequiresEscalation()
                || analysis.getRiskLevel() == RiskLevel.HIGH
                || analysis.getRiskLevel() == RiskLevel.CRITICAL;
        boolean mediumRiskTicket = analysis.getRiskLevel() == RiskLevel.MEDIUM && draft.isCreateTicket();
        boolean lowRiskButExplicitEscalation = analysis.getRiskLevel() == RiskLevel.LOW
                && containsEscalationCue(userMessage, analysis, draft, qaDecision);
        boolean createTicket = manualReviewRequired || highRiskEscalation || mediumRiskTicket || lowRiskButExplicitEscalation;
        boolean escalated = manualReviewRequired || highRiskEscalation || mediumRiskTicket || lowRiskButExplicitEscalation;
        boolean readyToSend = !escalated && !needsMoreInfo;

        String reason;
        String nextAction;
        if (manualReviewRequired) {
            reason = "QA 审核未直接放行，系统转入人工复核。";
            nextAction = "人工复核";
            createTicket = true;
            escalated = true;
            readyToSend = false;
        } else if (highRiskEscalation) {
            reason = needsMoreInfo
                    ? "命中了高风险或明确投诉场景，系统先创建工单并升级处理，同时继续向用户追问信息。"
                    : "命中了高风险或显式升级规则，需要进入人工或专家处理队列。";
            nextAction = needsMoreInfo ? "创建工单并追问用户" : "升级处理";
            createTicket = true;
            escalated = true;
            readyToSend = false;
        } else if (mediumRiskTicket) {
            reason = needsMoreInfo
                    ? "中风险场景下模型建议建单，系统先保留工单并继续向用户补充取证。"
                    : "中风险场景下模型建议建单，系统保留工单并进入待处理队列。";
            nextAction = needsMoreInfo ? "创建工单并追问用户" : "创建工单";
            createTicket = true;
            escalated = true;
            readyToSend = false;
        } else if (lowRiskButExplicitEscalation) {
            reason = needsMoreInfo
                    ? "虽然风险等级较低，但用户原话里出现了明确升级信号，系统先建单并继续补充信息。"
                    : "虽然风险等级较低，但用户原话里出现了明确升级信号，因此保留工单。";
            nextAction = needsMoreInfo ? "创建工单并追问用户" : "创建工单";
            createTicket = true;
            escalated = true;
            readyToSend = false;
        } else if (needsMoreInfo) {
            reason = "当前信息不足，优先向用户追问，避免在证据不足时建单或升级。";
            nextAction = "追问用户";
            createTicket = false;
            escalated = false;
            readyToSend = false;
        } else {
            reason = "信息充分且风险可控，系统直接输出回复，不触发升级。";
            nextAction = "发送回复";
        }

        String followupQuestion = needsMoreInfo
                ? (StringUtils.hasText(draft.getFollowupQuestion()) ? draft.getFollowupQuestion() : "请补充订单号、退款单号或退款发起时间，方便继续排查。")
                : "";
        return new WorkflowDecision(needsMoreInfo, createTicket, escalated, readyToSend, manualReviewRequired, nextAction, reason, followupQuestion);
    }

    private QaAudit saveQaAudit(String conversationId, QaDecision qaDecision) {
        QaAudit qaAudit = qaAuditRepository.findTop1ByConversationIdOrderByUpdatedAtDesc(conversationId)
                .orElseGet(QaAudit::new);
        qaAudit.setConversationId(conversationId);
        qaAudit.setReviewStatus(qaDecision.getReviewStatus());
        qaAudit.setIssuesJson(jsonHelper.writeValue(qaDecision.getIssues()));
        qaAudit.setRiskFlagsJson(jsonHelper.writeValue(qaDecision.getRiskFlags()));
        qaAudit.setApprovedReply(qaDecision.getFinalReply());
        qaAudit.setReviewSummary(qaDecision.getReviewSummary());
        return qaAuditRepository.save(qaAudit);
    }

    private AgentTask saveTask(String conversationId, String agentType, String subject, String input, String output) {
        AgentTask task = new AgentTask();
        task.setConversationId(conversationId);
        task.setAgentType(agentType);
        task.setSubject(subject);
        task.setStatus(TaskStatus.COMPLETED);
        task.setInputPayload(input);
        task.setOutputSummary(output);
        return agentTaskRepository.save(task);
    }

    private void appendUserEvent(String conversationId, String message) {
        conversationEventStore.append(conversationId, new ConversationEvent("USER", message, System.currentTimeMillis()));
    }

    private void appendAssistantEvent(String conversationId, String message) {
        conversationEventStore.append(conversationId, new ConversationEvent("ASSISTANT", message, System.currentTimeMillis()));
    }

    private String buildHistory(String conversationId) {
        List<ConversationEvent> events = conversationEventStore.list(conversationId);
        return events.stream()
                .map(event -> event.getRole() + ": " + event.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private boolean containsEscalationCue(String userMessage, IntakeAnalysis analysis, ResolutionDraft draft, QaDecision qaDecision) {
        String normalizedUserMessage = value(userMessage).toLowerCase(Locale.ROOT);
        return normalizedUserMessage.contains("投诉")
                || normalizedUserMessage.contains("complaint")
                || normalizedUserMessage.contains("转人工")
                || normalizedUserMessage.contains("人工处理")
                || normalizedUserMessage.contains("升级")
                || normalizedUserMessage.contains("工单")
                || normalizedUserMessage.contains("ticket")
                || normalizedUserMessage.contains("specialist")
                || normalizedUserMessage.contains("fraud")
                || normalizedUserMessage.contains("监管")
                || normalizedUserMessage.contains("曝光")
                || normalizedUserMessage.contains("举报")
                || normalizedUserMessage.contains("维权");
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
