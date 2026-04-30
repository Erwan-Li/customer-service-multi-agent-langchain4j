package com.customer.agent.service;

import com.customer.agent.config.RabbitConfig;
import com.customer.agent.dto.ConversationEvent;
import com.customer.agent.dto.MemorySummaryTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySummaryConsumer {

    private final ConversationEventStore conversationEventStore;
    private final LongTermMemoryService longTermMemoryService;

    @RabbitListener(queues = RabbitConfig.MEMORY_QUEUE)
    public void consume(MemorySummaryTask task) {
        if (task == null || task.getConversationId() == null) {
            return;
        }
        List<ConversationEvent> events = conversationEventStore.list(task.getConversationId());
        if (events.isEmpty()) {
            return;
        }
        String transcript = events.stream()
                .map(event -> event.getRole() + ": " + event.getContent())
                .collect(Collectors.joining("\n\n"));
        String latest = events.stream().map(ConversationEvent::getContent).reduce((first, second) -> second).orElse("");
        longTermMemoryService.rememberConversation(task.getConversationId(), task.getUserId(), transcript, latest, List.of("customer-service", "conversation"));
        log.info("Long-term memory persisted for conversation {}", task.getConversationId());
    }
}
