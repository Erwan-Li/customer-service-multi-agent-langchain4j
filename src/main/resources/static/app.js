const form = document.getElementById("message-form");
const conversationList = document.getElementById("conversation-list");
const ticketList = document.getElementById("ticket-list");

const STATUS_MAP = {
    NEW: "新建",
    UNDERSTANDING: "理解中",
    RETRIEVING: "检索中",
    DRAFTING: "生成中",
    REVIEWING: "审核中",
    NEEDS_INFO: "待补充信息",
    READY_TO_SEND: "可回复",
    ESCALATED: "已升级",
    CLOSED: "已关闭",
    CREATED: "已创建",
    QUEUED: "排队中",
    ASSIGNED: "已分配",
    IN_PROGRESS: "处理中",
    WAITING_USER: "待用户回复",
    WAITING_INTERNAL: "待内部处理",
    RESOLVED: "已解决",
    REOPENED: "重新打开",
    AUTO_REVIEWED: "自动审核",
    PASSED: "通过",
    FAILED: "未通过",
    FLAGGED: "已标记",
    MANUAL_REVIEW_REQUIRED: "需人工复核",
    COMPLETED: "已完成",
    PENDING: "待处理"
};

const RISK_MAP = {
    LOW: "低",
    MEDIUM: "中",
    HIGH: "高",
    CRITICAL: "紧急"
};

const SOURCE_TYPE_MAP = {
    FAQ: "FAQ",
    SOP: "SOP",
    HISTORY: "历史工单"
};

const AGENT_TYPE_MAP = {
    IntakeAgent: "Intake 意图识别",
    KnowledgeAgent: "Knowledge 知识检索",
    ResolutionAgent: "Resolution 回复生成",
    QAAgent: "QA 质量审核",
    SummaryAgent: "Summary 总结沉淀"
};

const STRATEGY_MAP = {
    DIRECT: "直接回答",
    LIKELY_MATCH: "中置信参考",
    AMBIGUOUS: "多候选澄清",
    NO_CONFIDENCE: "低置信追问",
    GUARDRAIL: "风险保护",
    FALLBACK: "兜底模板"
};

const WORKFLOW_STATES = [
    ["UNDERSTANDING", "理解"],
    ["RETRIEVING", "检索"],
    ["DRAFTING", "生成"],
    ["REVIEWING", "审核"],
    ["READY_TO_SEND", "可发送"],
    ["NEEDS_INFO", "追问"],
    ["ESCALATED", "升级"]
];

async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        headers: {"Content-Type": "application/json"},
        ...options
    });
    if (!response.ok) {
        throw new Error(await response.text());
    }
    return response.json();
}

function yesNo(value) {
    return value ? "是" : "否";
}

function text(value, fallback = "暂无") {
    return value === null || value === undefined || value === "" ? fallback : String(value);
}

function byId(id) {
    return document.getElementById(id);
}

function setText(id, value) {
    const element = byId(id);
    if (element) {
        element.textContent = value;
    }
}

function translateStatus(value) {
    return STATUS_MAP[value] || value || "未知";
}

function translateRisk(value) {
    return RISK_MAP[value] || value || "未知";
}

function translateSourceType(value) {
    return SOURCE_TYPE_MAP[value] || value || "未知来源";
}

function translateAgentType(value) {
    return AGENT_TYPE_MAP[value] || value || "未知 Agent";
}

function formatIntent(value) {
    const map = {
        product_info: "商品信息咨询",
        general_inquiry: "一般咨询",
        general_service: "通用客服",
        shelf_life_query: "保质期查询",
        expiry_usage: "过期后是否可用",
        refund: "退款问题",
        complaint: "投诉",
        subscription: "续费问题"
    };
    return map[value] || value || "未识别";
}

function parseJsonSafe(value) {
    if (!value) {
        return {};
    }
    try {
        return JSON.parse(value);
    } catch {
        return {};
    }
}

function createStackItem(title, subtitle, body) {
    const div = document.createElement("div");
    div.className = "stack-item";
    const titleNode = document.createElement("strong");
    const subtitleNode = document.createElement("span");
    const bodyNode = document.createElement("p");
    titleNode.textContent = title || "";
    subtitleNode.textContent = subtitle || "";
    bodyNode.textContent = body || "";
    div.append(titleNode, subtitleNode, bodyNode);
    return div;
}

function renderOverview(data) {
    conversationList.innerHTML = "";
    (data.conversations || []).forEach(item => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "list-item clickable";
        const titleNode = document.createElement("strong");
        const statusNode = document.createElement("span");
        const metaNode = document.createElement("small");
        const previewNode = document.createElement("p");
        titleNode.textContent = item.userId || "";
        statusNode.textContent = translateStatus(item.status);
        metaNode.textContent = `${formatIntent(item.intent)} / ${formatIntent(item.subIntent)}`;
        previewNode.textContent = item.lastMessagePreview || "";
        button.append(titleNode, statusNode, metaNode, previewNode);
        button.addEventListener("click", () => loadConversationDetail(item.id));
        conversationList.appendChild(button);
    });

    if (!(data.conversations || []).length) {
        conversationList.innerHTML = `<div class="list-item muted">暂无会话记录</div>`;
    }

    ticketList.innerHTML = "";
    (data.tickets || []).forEach(item => {
        ticketList.appendChild(
            createStackItem(
                formatIntent(item.category),
                `${translateStatus(item.status)} / ${translateRisk(item.priority)}`,
                item.summary
            )
        );
    });

    if (!(data.tickets || []).length) {
        ticketList.innerHTML = `<div class="list-item muted">暂无工单记录</div>`;
    }
}

function renderStateFlow(data) {
    const stateFlow = byId("state-flow");
    if (!stateFlow) {
        return;
    }
    const tasks = data.agentTasks || [];
    const completedAgents = new Set(tasks.map(item => item.agentType));
    const current = data.conversation?.status;
    const terminal = new Set(["READY_TO_SEND", "NEEDS_INFO", "ESCALATED", "CLOSED"]);

    const stateToAgent = {
        UNDERSTANDING: "IntakeAgent",
        RETRIEVING: "KnowledgeAgent",
        DRAFTING: "ResolutionAgent",
        REVIEWING: "QAAgent"
    };

    stateFlow.innerHTML = "";
    WORKFLOW_STATES.forEach(([status, label]) => {
        const agent = stateToAgent[status];
        const done = agent ? completedAgents.has(agent) : current === status || terminal.has(current);
        const active = current === status;
        const item = document.createElement("div");
        item.className = `state-node ${done ? "done" : ""} ${active ? "active" : ""}`;
        const markerNode = document.createElement("span");
        const labelNode = document.createElement("strong");
        markerNode.textContent = done ? "✓" : "";
        labelNode.textContent = label;
        item.append(markerNode, labelNode);
        stateFlow.appendChild(item);
    });
}

function formatEvidenceBody(item) {
    const reasons = (item.matchReasons || []).join("、") || "无";
    return [
        item.content || "暂无内容",
        "",
        `召回分：${Number(item.retrievalScore || 0).toFixed(3)}`,
        `重排分：${Number(item.rerankScore || 0).toFixed(3)}`,
        `最终分：${Number(item.score || 0).toFixed(3)}`,
        `命中原因：${reasons}`
    ].join("\n");
}

function getKnowledgeData(data) {
    const task = (data.agentTasks || []).find(item => item.agentType === "KnowledgeAgent");
    return parseJsonSafe(task?.outputSummary);
}

function renderReactPanel(data) {
    const panel = byId("react-panel");
    if (!panel) {
        return;
    }
    const knowledge = getKnowledgeData(data);
    const evidences = knowledge.evidences || data.evidences || [];
    const sourceCounts = evidences.reduce((acc, item) => {
        acc[item.sourceType] = (acc[item.sourceType] || 0) + 1;
        return acc;
    }, {});

    panel.innerHTML = "";
    const overview = document.createElement("div");
    overview.className = "react-summary";
    overview.innerHTML = `
        <div><span>证据是否足够</span><strong>${yesNo(knowledge.grounded)}</strong></div>
        <div><span>置信等级</span><strong>${text(knowledge.confidenceLevel, "LOW")}</strong></div>
        <div><span>证据数量</span><strong>${evidences.length}</strong></div>
        <div><span>来源分布</span><strong>${Object.entries(sourceCounts).map(([k, v]) => `${translateSourceType(k)} ${v}`).join(" / ") || "无"}</strong></div>
    `;
    panel.appendChild(overview);

    const explanation = createStackItem(
        "Evidence Assessment",
        `confidence=${text(knowledge.confidenceLevel, "LOW")} score=${Number(knowledge.confidence || 0).toFixed(3)}`,
        [
            `reason: ${text(knowledge.confidenceExplanation)}`,
            `supportRoute: ${text(knowledge.supportRoute)}`,
            `likelyMatchedProduct: ${text(knowledge.likelyMatchedProduct, "无")}`,
            `likelyMatchedGroup: ${text(knowledge.likelyMatchedGroup, "无")}`
        ].join("\n")
    );
    panel.appendChild(explanation);

    const steps = knowledge.reactSteps || [];
    if (steps.length) {
        steps.forEach((step, index) => {
            panel.appendChild(createStackItem(`ReAct Step ${index + 1}`, "", step));
        });
    } else {
        panel.appendChild(createStackItem("ReAct Trace", "", "当前会话没有返回 reactSteps。请确认后端已重启，并重新发起一次请求。"));
    }
}

function formatTaskBody(item) {
    const data = parseJsonSafe(item.outputSummary);
    switch (item.agentType) {
        case "IntakeAgent":
            return [
                `摘要：${text(data.summary)}`,
                `意图：${formatIntent(data.intent)}`,
                `子意图：${formatIntent(data.subIntent)}`,
                `领域：${formatIntent(data.domain)}`,
                `风险等级：${translateRisk(data.riskLevel)}`,
                `缺失信息：${(data.missingInfo || []).join("、") || "无"}`
            ].join("\n");
        case "KnowledgeAgent":
            return [
                `命中证据数：${(data.evidences || []).length}`,
                `证据足够：${yesNo(data.grounded)}`,
                `置信等级：${text(data.confidenceLevel, "LOW")}`,
                `综合分：${Number(data.confidence || 0).toFixed(3)}`,
                `说明：${text(data.confidenceExplanation)}`,
                `ReAct 步数：${(data.reactSteps || []).length}`
            ].join("\n");
        case "ResolutionAgent":
            return [
                `回复策略：${STRATEGY_MAP[data.replyStrategy] || data.replyStrategy || "未知"}`,
                `建议回复：${text(data.customerReply)}`,
                `内部建议：${text(data.internalSuggestion)}`,
                `是否追问：${yesNo(data.needMoreInfo)}`,
                `是否建单：${yesNo(data.createTicket)}`
            ].join("\n");
        case "QAAgent":
            return [
                `审核状态：${translateStatus(data.reviewStatus)}`,
                `是否通过：${yesNo(data.approved)}`,
                `审核结论：${text(data.reviewSummary)}`,
                `风险标记：${(data.riskFlags || []).join("、") || "无"}`
            ].join("\n");
        case "SummaryAgent":
            return [
                `会话摘要：${text(data.conversationSummary)}`,
                `工单摘要：${text(data.ticketSummary)}`,
                `质量结论：${text(data.qualitySummary)}`,
                `标签：${(data.tags || []).join("、") || "无"}`
            ].join("\n");
        default:
            return item.outputSummary || "暂无";
    }
}

function renderResult(data) {
    byId("result-empty")?.classList.add("hidden");
    byId("result-content")?.classList.remove("hidden");

    setText("active-conversation", data.conversation.id);
    setText("conversation-status", translateStatus(data.conversation.status));
    setText("risk-level", translateRisk(data.analysis.riskLevel));
    setText("ticket-status", data.ticket ? translateStatus(data.ticket.status) : "未建单");
    setText("qa-status", data.qaAudit ? translateStatus(data.qaAudit.reviewStatus) : "无审核");

    renderStateFlow(data);
    renderReactPanel(data);

    setText("next-action", data.decision?.nextAction || "暂无");
    setText("needs-more-info", yesNo(data.decision?.needsMoreInfo));
    setText("creates-ticket", yesNo(data.decision?.createTicket));
    setText("is-escalated", yesNo(data.decision?.escalated));
    setText("decision-reason", data.decision?.reason || "");

    const followupBox = byId("followup-box");
    const followupQuestion = data.decision?.followupQuestion || "";
    if (followupBox && followupQuestion) {
        followupBox.classList.remove("hidden");
        setText("followup-question", followupQuestion);
    } else if (followupBox) {
        followupBox.classList.add("hidden");
        setText("followup-question", "");
    }

    setText("analysis-intent", formatIntent(data.analysis.intent));
    setText("analysis-sub-intent", formatIntent(data.analysis.subIntent));
    setText("analysis-domain", formatIntent(data.analysis.domain));
    setText("analysis-sentiment", data.analysis.sentiment || "未知");
    setText("analysis-summary", data.analysis.summary || "暂无");
    setText("assistant-reply", data.finalReply || "");
    setText("internal-suggestion", data.internalSuggestion || "暂无");

    const evidenceList = byId("evidence-list");
    if (!evidenceList) {
        return;
    }
    evidenceList.innerHTML = "";
    (data.evidences || []).forEach(item => {
        evidenceList.appendChild(
            createStackItem(
                item.title || "未命名证据",
                `${translateSourceType(item.sourceType)} / 最终分=${Number(item.score || 0).toFixed(3)}`,
                formatEvidenceBody(item)
            )
        );
    });
    if (!(data.evidences || []).length) {
        evidenceList.innerHTML = `<div class="stack-item muted">暂无检索证据</div>`;
    }

    const taskList = byId("task-list");
    if (!taskList) {
        return;
    }
    taskList.innerHTML = "";
    (data.agentTasks || []).forEach(item => {
        taskList.appendChild(
            createStackItem(
                translateAgentType(item.agentType),
                translateStatus(item.status),
                formatTaskBody(item)
            )
        );
    });
    if (!(data.agentTasks || []).length) {
        taskList.innerHTML = `<div class="stack-item muted">暂无 Agent 执行记录</div>`;
    }

    const qaReview = byId("qa-review");
    if (!qaReview) {
        return;
    }
    qaReview.innerHTML = "";
    if (data.qaAudit) {
        qaReview.appendChild(createStackItem("审核总结", translateStatus(data.qaAudit.reviewStatus), data.qaAudit.reviewSummary));
        qaReview.appendChild(createStackItem("审核回复", "最终回复", data.qaAudit.approvedReply));
        qaReview.appendChild(createStackItem("问题列表", "issues", data.qaAudit.issuesJson));
    } else {
        qaReview.innerHTML = `<div class="stack-item muted">暂无 QA 记录</div>`;
    }

    const ticketDetail = byId("ticket-detail");
    if (!ticketDetail) {
        return;
    }
    ticketDetail.innerHTML = "";
    if (data.ticket) {
        ticketDetail.appendChild(
            createStackItem(
                `工单 ${data.ticket.id}`,
                `${translateStatus(data.ticket.status)} / ${translateRisk(data.ticket.priority)}`,
                data.ticket.summary
            )
        );
    } else {
        ticketDetail.innerHTML = `<div class="stack-item muted">当前流程未创建工单</div>`;
    }
}

async function refreshOverview() {
    const data = await fetchJson("/api/dashboard/overview");
    renderOverview(data);
}

async function loadConversationDetail(conversationId) {
    const data = await fetchJson(`/api/workflow/conversations/${conversationId}`);
    renderResult(data);
    if (byId("conversationId")) byId("conversationId").value = data.conversation.id;
    if (byId("userId")) byId("userId").value = data.conversation.userId;
    if (byId("channel")) byId("channel").value = data.conversation.channel;
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    const conversationIdInput = document.getElementById("conversationId");
    const existingConversationId = conversationIdInput.value.trim();
    const payload = {
        conversationId: existingConversationId || null,
        userId: document.getElementById("userId").value,
        channel: document.getElementById("channel").value,
        message: document.getElementById("message").value
    };

    try {
        const result = await fetchJson("/api/workflow/message", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        renderResult(result);
        conversationIdInput.value = result.conversation.id;
        await refreshOverview();
    } catch (error) {
        alert(`请求失败：${error.message}`);
    }
});

document.getElementById("refresh-overview").addEventListener("click", () => {
    refreshOverview().catch(error => alert(`刷新失败：${error.message}`));
});

document.getElementById("new-conversation").addEventListener("click", () => {
    if (byId("conversationId")) byId("conversationId").value = "";
    if (byId("message")) byId("message").value = "";
    byId("result-content")?.classList.add("hidden");
    byId("result-empty")?.classList.remove("hidden");
    setText("active-conversation", "尚未选择会话");
});

document.getElementById("reset-demo-data").addEventListener("click", async () => {
    const confirmed = window.confirm("这会清空当前演示产生的会话、工单、Redis 事件和 ES 历史记忆。是否继续？");
    if (!confirmed) {
        return;
    }
    try {
        const result = await fetchJson("/api/admin/reset-demo-data", {method: "POST"});
        byId("result-content")?.classList.add("hidden");
        byId("result-empty")?.classList.remove("hidden");
        if (byId("conversationId")) byId("conversationId").value = "";
        if (byId("message")) byId("message").value = "";
        await refreshOverview();
        alert(result.message);
    } catch (error) {
        alert(`重置失败：${error.message}`);
    }
});

refreshOverview().catch(console.error);
