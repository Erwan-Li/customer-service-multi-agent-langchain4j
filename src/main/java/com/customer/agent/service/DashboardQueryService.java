package com.customer.agent.service;

import com.customer.agent.dto.ConversationView;
import com.customer.agent.dto.OverviewResponse;
import com.customer.agent.dto.TicketView;
import com.customer.agent.repository.ConversationRepository;
import com.customer.agent.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final ConversationRepository conversationRepository;
    private final TicketRepository ticketRepository;

    public OverviewResponse overview() {
        return new OverviewResponse(
                conversationRepository.findTop20ByOrderByUpdatedAtDesc().stream()
                        .map(item -> new ConversationView(
                                item.getId(),
                                item.getUserId(),
                                item.getStatus(),
                                item.getIntent(),
                                item.getSubIntent(),
                                item.getLatestUserMessage()))
                        .toList(),
                ticketRepository.findTop20ByOrderByUpdatedAtDesc().stream()
                        .map(item -> new TicketView(
                                item.getId(),
                                item.getCategory(),
                                item.getPriority(),
                                item.getStatus(),
                                item.getSummary()))
                        .toList());
    }
}
