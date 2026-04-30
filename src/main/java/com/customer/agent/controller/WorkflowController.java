package com.customer.agent.controller;

import com.customer.agent.dto.MessageRequest;
import com.customer.agent.dto.OverviewResponse;
import com.customer.agent.dto.WorkflowDetailResponse;
import com.customer.agent.dto.WorkflowResponse;
import com.customer.agent.dto.AdminActionResponse;
import com.customer.agent.service.AdminMaintenanceService;
import com.customer.agent.service.ConversationWorkflowService;
import com.customer.agent.service.DashboardQueryService;
import com.customer.agent.service.WorkflowDetailQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowController {

    private final ConversationWorkflowService conversationWorkflowService;
    private final DashboardQueryService dashboardQueryService;
    private final WorkflowDetailQueryService workflowDetailQueryService;
    private final AdminMaintenanceService adminMaintenanceService;

    @PostMapping("/workflow/message")
    public WorkflowResponse process(@Valid @RequestBody MessageRequest request) {
        return conversationWorkflowService.process(request);
    }

    @GetMapping("/workflow/conversations/{conversationId}")
    public WorkflowDetailResponse detail(@PathVariable String conversationId) {
        return workflowDetailQueryService.detail(conversationId);
    }

    @GetMapping("/dashboard/overview")
    public OverviewResponse overview() {
        return dashboardQueryService.overview();
    }

    @PostMapping("/admin/reset-demo-data")
    public AdminActionResponse resetDemoData() {
        adminMaintenanceService.resetDemoData();
        return new AdminActionResponse(true, "演示数据已清空，知识库已重新初始化。");
    }
}
