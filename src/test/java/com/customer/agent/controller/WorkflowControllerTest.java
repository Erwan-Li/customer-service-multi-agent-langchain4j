package com.customer.agent.controller;

import com.customer.agent.dto.IntakeAnalysis;
import com.customer.agent.dto.KnowledgeEvidenceView;
import com.customer.agent.dto.MessageRequest;
import com.customer.agent.dto.OverviewResponse;
import com.customer.agent.dto.WorkflowDecision;
import com.customer.agent.dto.WorkflowResponse;
import com.customer.agent.entity.Conversation;
import com.customer.agent.enums.ConversationStatus;
import com.customer.agent.enums.RiskLevel;
import com.customer.agent.service.ConversationWorkflowService;
import com.customer.agent.service.DashboardQueryService;
import com.customer.agent.service.WorkflowDetailQueryService;
import com.customer.agent.service.AdminMaintenanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConversationWorkflowService conversationWorkflowService;

    @MockBean
    private DashboardQueryService dashboardQueryService;

    @MockBean
    private WorkflowDetailQueryService workflowDetailQueryService;

    @MockBean
    private AdminMaintenanceService adminMaintenanceService;

    @Test
    void shouldProcessWorkflowRequest() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setId("conv-1");
        conversation.setUserId("user-1");
        conversation.setChannel("app");
        conversation.setStatus(ConversationStatus.READY_TO_SEND);
        conversation.setRiskLevel(RiskLevel.LOW);

        IntakeAnalysis analysis = new IntakeAnalysis();
        analysis.setIntent("refund");
        analysis.setSubIntent("refund_delay");
        analysis.setSummary("Refund delay");
        analysis.setRiskLevel(RiskLevel.LOW);

        WorkflowResponse response = new WorkflowResponse(
                conversation,
                analysis,
                List.of(new KnowledgeEvidenceView("FAQ", "退款到账延迟", "银行卡退款通常 1 到 7 个工作日到账。", 1.2, 1.2, 0.0, List.of("测试数据"))),
                "Please wait patiently for the refund to arrive.",
                "No ticket is needed for now.",
                null,
                null,
                List.of(),
                new WorkflowDecision(false, false, false, true, false, "Send reply", "Enough information was found.", "")
        );

        when(conversationWorkflowService.process(any(MessageRequest.class))).thenReturn(response);
        when(dashboardQueryService.overview()).thenReturn(new OverviewResponse(List.of(), List.of()));

        mockMvc.perform(post("/api/workflow/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.id").value("conv-1"))
                .andExpect(jsonPath("$.analysis.intent").value("refund"))
                .andExpect(jsonPath("$.decision.nextAction").value("Send reply"))
                .andExpect(jsonPath("$.decision.createTicket").value(false));

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk());
    }

    private MessageRequest buildRequest() {
        MessageRequest request = new MessageRequest();
        request.setUserId("user-1");
        request.setChannel("app");
        request.setMessage("Why has my refund not arrived?");
        return request;
    }
}
