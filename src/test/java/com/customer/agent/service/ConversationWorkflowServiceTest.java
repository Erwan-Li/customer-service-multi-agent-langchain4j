package com.customer.agent.service;

import com.customer.agent.agent.IntakeAgent;
import com.customer.agent.agent.KnowledgeAgent;
import com.customer.agent.agent.QaAgent;
import com.customer.agent.agent.ResolutionAgent;
import com.customer.agent.agent.SummaryAgent;
import com.customer.agent.dto.ConversationSummary;
import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeBundle;
import com.customer.agent.dto.MessageRequest;
import com.customer.agent.dto.QaDecision;
import com.customer.agent.dto.ResolutionDraft;
import com.customer.agent.dto.WorkflowResponse;
import com.customer.agent.entity.AgentTask;
import com.customer.agent.entity.Conversation;
import com.customer.agent.entity.QaAudit;
import com.customer.agent.enums.ConversationStatus;
import com.customer.agent.enums.QaReviewStatus;
import com.customer.agent.enums.RiskLevel;
import com.customer.agent.repository.AgentTaskRepository;
import com.customer.agent.repository.ConversationRepository;
import com.customer.agent.repository.QaAuditRepository;
import com.customer.agent.repository.TicketRepository;
import com.customer.agent.util.JsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationWorkflowServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private AgentTaskRepository agentTaskRepository;
    @Mock
    private QaAuditRepository qaAuditRepository;
    @Mock
    private ConversationEventStore conversationEventStore;
    @Mock
    private LongTermMemoryService longTermMemoryService;
    @Mock
    private MemorySummaryProducer memorySummaryProducer;
    @Mock
    private IntakeAgent intakeAgent;
    @Mock
    private KnowledgeAgent knowledgeAgent;
    @Mock
    private ResolutionAgent resolutionAgent;
    @Mock
    private QaAgent qaAgent;
    @Mock
    private SummaryAgent summaryAgent;
    @Mock
    private JsonHelper jsonHelper;

    private ConversationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ConversationWorkflowService(
                conversationRepository,
                ticketRepository,
                agentTaskRepository,
                qaAuditRepository,
                conversationEventStore,
                longTermMemoryService,
                memorySummaryProducer,
                intakeAgent,
                knowledgeAgent,
                resolutionAgent,
                qaAgent,
                summaryAgent,
                jsonHelper
        );
    }

    @Test
    void shouldKeepLowRiskConversationReadyToSendEvenIfModelSuggestsTicket() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) {
                conversation.prePersist();
            }
            return conversation;
        });
        when(qaAuditRepository.findTop1ByConversationIdOrderByUpdatedAtDesc(any())).thenReturn(Optional.empty());
        when(qaAuditRepository.save(any(QaAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentTaskRepository.save(any(AgentTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentTaskRepository.findByConversationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(longTermMemoryService.loadUserProfile(any())).thenReturn("");
        doNothing().when(longTermMemoryService).rememberQaFeedback(any(), any(), any(), any());
        doNothing().when(memorySummaryProducer).send(any());
        doNothing().when(conversationEventStore).append(any(), any());
        when(conversationEventStore.list(any())).thenReturn(List.of());
        when(jsonHelper.writeValue(any())).thenReturn("{}");

        IntakeAnalysis analysis = new IntakeAnalysis();
        analysis.setSummary("Refund delay");
        analysis.setIntent("refund");
        analysis.setSubIntent("refund_delay");
        analysis.setRiskLevel(RiskLevel.LOW);
        analysis.setRequiresEscalation(false);
        analysis.setMissingInfo(List.of());
        when(intakeAgent.run(any(), any(), any())).thenReturn(analysis);

        KnowledgeBundle knowledgeBundle = new KnowledgeBundle();
        knowledgeBundle.setGrounded(true);
        when(knowledgeAgent.run(any(), any(), any())).thenReturn(knowledgeBundle);

        ResolutionDraft draft = new ResolutionDraft();
        draft.setCustomerReply("Card refunds usually take one to seven business days.");
        draft.setInternalSuggestion("Provide a standard refund timeline explanation.");
        draft.setCreateTicket(true);
        draft.setNeedMoreInfo(false);
        when(resolutionAgent.run(any(), any(), any())).thenReturn(draft);

        QaDecision qaDecision = new QaDecision();
        qaDecision.setApproved(true);
        qaDecision.setReviewStatus(QaReviewStatus.PASSED);
        qaDecision.setFinalReply("Card refunds usually take one to seven business days.");
        qaDecision.setReviewSummary("Approved.");
        when(qaAgent.run(any(), any(), any())).thenReturn(qaDecision);

        ConversationSummary summary = new ConversationSummary();
        summary.setConversationSummary("Refund delay consultation");
        summary.setTags(List.of("refund", "delay"));
        when(summaryAgent.run(any(), any(), any(), any())).thenReturn(summary);

        WorkflowResponse response = service.process(buildRequest());

        assertEquals(ConversationStatus.READY_TO_SEND, response.conversation().getStatus());
        assertFalse(response.decision().createTicket());
        assertFalse(response.decision().escalated());
        assertNull(response.ticket());
    }

    private MessageRequest buildRequest() {
        MessageRequest request = new MessageRequest();
        request.setUserId("user-1");
        request.setChannel("app");
        request.setMessage("Why has my refund not arrived yet?");
        return request;
    }
}
